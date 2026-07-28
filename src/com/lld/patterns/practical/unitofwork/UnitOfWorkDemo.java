package com.lld.patterns.practical.unitofwork;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UNIT OF WORK — track everything that changed during a business transaction and write it out once.
 *
 * <p>Repositories answer "where does the database fit?". Unit of Work answers the follow-up that
 * always comes next: <b>"you saved the order and the customer through two different repositories -
 * what happens if the second save fails?"</b>
 *
 * <p>Two distinct problems, and you should name both:
 * <ol>
 *   <li><b>Consistency.</b> Save-as-you-go means a failure halfway leaves the database in a state
 *       your domain considers impossible - an order with no customer, a transfer that debited but
 *       never credited.</li>
 *   <li><b>Chattiness.</b> Saving inside a loop is one network round trip per object. Collecting the
 *       changes and flushing once turns 200 round trips into one batch.</li>
 * </ol>
 *
 * <p><b>The nuance that earns the point.</b> Unit of Work does not itself make anything atomic - the
 * database transaction does. What it gives you is control over <em>what</em> is sent and <em>when</em>,
 * so that a single {@code BEGIN ... COMMIT} can wrap the whole business operation. Say "the UoW
 * decides what to flush; the transaction makes it atomic" and you have the relationship right.
 *
 * <p><b>In the wild:</b> JPA's {@code EntityManager} is exactly this - it is why changing a managed
 * entity's field persists without ever calling {@code save()}, and why everything hits the database
 * at flush time rather than at assignment time. Spring's {@code @Transactional} draws the boundary.
 */
interface Entity {
    String id();
}

class Customer implements Entity {

    private final String id;
    private String email;

    Customer(String id, String email) {
        this.id = id;
        this.email = email;
    }

    @Override
    public String id() {
        return id;
    }

    void changeEmail(String email) {
        this.email = email;
    }

    @Override
    public String toString() {
        return "Customer[" + id + ", " + email + "]";
    }
}

/** Stands in for the database. Counts round trips so the batching claim is visible, not asserted. */
class FakeDatabase {

    private final Map<String, String> rows = new LinkedHashMap<>();
    private final List<String> statements = new ArrayList<>();
    private int roundTrips;

    void execute(String statement, String id, String value) {
        roundTrips++;
        statements.add(statement + " " + id);
        if ("DELETE".equals(statement)) {
            rows.remove(id);
        } else {
            rows.put(id, value);
        }
    }

    /** One round trip carrying many statements - what a real batch flush looks like. */
    void executeBatch(List<Runnable> batch) {
        int before = roundTrips;
        batch.forEach(Runnable::run);
        roundTrips = before + 1; // the whole batch is ONE trip
    }

    int roundTrips() {
        return roundTrips;
    }

    List<String> statements() {
        return List.copyOf(statements);
    }

    Map<String, String> rows() {
        return Map.copyOf(rows);
    }

    void reset() {
        rows.clear();
        statements.clear();
        roundTrips = 0;
    }
}

class UnitOfWork {

    // LinkedHashMap on all three: insertion order is the order the statements will be issued, and
    // reproducible ordering is what stops two concurrent transactions deadlocking on the same rows.
    private final Map<String, Entity> pendingInserts = new LinkedHashMap<>();
    private final Map<String, Entity> pendingUpdates = new LinkedHashMap<>();
    private final Map<String, Entity> pendingDeletes = new LinkedHashMap<>();

    private final FakeDatabase database;

    UnitOfWork(FakeDatabase database) {
        this.database = database;
    }

    void registerNew(Entity entity) {
        pendingInserts.put(entity.id(), entity);
    }

    /**
     * Marks an existing entity as changed.
     *
     * <p>Deliberately a no-op if the entity is already pending insert: it is still one INSERT, just
     * with different values. Emitting INSERT-then-UPDATE for an object that never existed in the
     * database is the classic naive-implementation bug.
     */
    void registerDirty(Entity entity) {
        if (pendingInserts.containsKey(entity.id())) {
            return;
        }
        pendingUpdates.put(entity.id(), entity);
    }

    /**
     * Marks an entity for deletion.
     *
     * <p>If it was only ever pending insert, the two cancel out and the database never hears about
     * it at all. That cancellation is free correctness <em>and</em> free performance, and it only
     * exists because writes were deferred.
     */
    void registerRemoved(Entity entity) {
        if (pendingInserts.remove(entity.id()) != null) {
            return; // created and destroyed inside the same transaction - a no-op
        }
        pendingUpdates.remove(entity.id()); // no point updating a row we are about to delete
        pendingDeletes.put(entity.id(), entity);
    }

    /**
     * Writes everything out in one flush.
     *
     * <p><b>Order matters and is not arbitrary:</b> inserts, then updates, then deletes. Deleting
     * first can violate a foreign key that a pending insert was about to satisfy. Real ORMs use
     * exactly this ordering for exactly this reason.
     */
    void commit() {
        List<Runnable> batch = new ArrayList<>();
        pendingInserts.forEach((id, e) -> batch.add(() -> database.execute("INSERT", id, e.toString())));
        pendingUpdates.forEach((id, e) -> batch.add(() -> database.execute("UPDATE", id, e.toString())));
        pendingDeletes.forEach((id, e) -> batch.add(() -> database.execute("DELETE", id, e.toString())));

        database.executeBatch(batch);
        clear();
    }

    /** Throw the change set away. Nothing was written, so there is nothing to undo. */
    void rollback() {
        clear();
    }

    String pending() {
        return pendingInserts.size() + " insert(s), " + pendingUpdates.size()
                + " update(s), " + pendingDeletes.size() + " delete(s)";
    }

    private void clear() {
        pendingInserts.clear();
        pendingUpdates.clear();
        pendingDeletes.clear();
    }
}

public class UnitOfWorkDemo {

    public static void main(String[] args) {
        FakeDatabase database = new FakeDatabase();

        section("1. Save-as-you-go: one round trip per object");
        for (int i = 1; i <= 5; i++) {
            database.execute("INSERT", "C" + i, "Customer[C" + i + "]");
        }
        System.out.println("  5 customers saved individually -> " + database.roundTrips() + " round trips");
        System.out.println("  And if trip 3 fails, trips 1 and 2 are already committed. The domain now");
        System.out.println("  contains a state it believes is impossible.");

        section("2. Same work through a Unit of Work");
        database.reset();
        UnitOfWork uow = new UnitOfWork(database);
        for (int i = 1; i <= 5; i++) {
            uow.registerNew(new Customer("C" + i, "c" + i + "@example.com"));
        }
        System.out.println("  after registering 5   : " + uow.pending());
        System.out.println("  database round trips  : " + database.roundTrips() + "   (nothing sent yet)");
        uow.commit();
        System.out.println("  after commit          : " + database.roundTrips() + " round trip, "
                + database.statements().size() + " statements");
        System.out.println("  One flush, one transaction boundary, all-or-nothing.");

        section("3. Created and deleted in the same transaction never reaches the database");
        database.reset();
        UnitOfWork cancelling = new UnitOfWork(database);
        Customer temp = new Customer("C9", "temp@example.com");
        cancelling.registerNew(temp);
        cancelling.registerRemoved(temp);
        System.out.println("  pending after new + removed : " + cancelling.pending());
        cancelling.commit();
        System.out.println("  statements issued           : " + database.statements());
        System.out.println("  Deferring writes is what makes this cancellation possible at all.");

        section("4. Created then modified is still a single INSERT");
        database.reset();
        UnitOfWork merging = new UnitOfWork(database);
        Customer fresh = new Customer("C10", "old@example.com");
        merging.registerNew(fresh);
        fresh.changeEmail("new@example.com");
        merging.registerDirty(fresh);
        merging.commit();
        System.out.println("  statements : " + database.statements());
        System.out.println("  row        : " + database.rows().get("C10"));
        System.out.println("  Not INSERT-then-UPDATE. The naive implementation emits both.");

        section("5. Statement ordering is deliberate");
        database.reset();
        UnitOfWork ordered = new UnitOfWork(database);
        Customer doomed = new Customer("C20", "gone@example.com");
        database.execute("INSERT", "C20", doomed.toString());
        database.reset();

        ordered.registerRemoved(doomed);
        ordered.registerNew(new Customer("C21", "new@example.com"));
        ordered.registerDirty(new Customer("C22", "changed@example.com"));
        ordered.commit();
        System.out.println("  " + database.statements());
        System.out.println("  Inserts, then updates, then deletes - registration order is irrelevant.");
        System.out.println("  Delete-first can break a foreign key that a pending insert was about to satisfy.");

        section("6. Rollback is free, because nothing was written");
        database.reset();
        UnitOfWork abandoned = new UnitOfWork(database);
        abandoned.registerNew(new Customer("C30", "never@example.com"));
        abandoned.registerNew(new Customer("C31", "never@example.com"));
        System.out.println("  pending before rollback : " + abandoned.pending());
        abandoned.rollback();
        System.out.println("  pending after rollback  : " + abandoned.pending());
        System.out.println("  round trips             : " + database.roundTrips());
        System.out.println("  Contrast with save-as-you-go, where rollback means writing compensating");
        System.out.println("  statements to undo what you already committed - and hoping THOSE succeed.");

        System.out.println("\nDone.");
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
