package patterns.practical.idempotency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * IDEMPOTENCY KEY — make "do it again" safe.
 *
 * <p>This is not in any pattern catalogue, and it is asked constantly, usually as:
 * <b>"the client's request timed out and they retried - how do you avoid charging the card twice?"</b>
 *
 * <p>The reason it is unavoidable is a genuinely unsolvable ambiguity: when a response is lost, the
 * client cannot distinguish "the server never got it" from "the server did it and the reply
 * vanished". Retrying is the only sane client behaviour, so the <em>server</em> has to make retries
 * harmless. Any at-least-once delivery system - HTTP retries, message queues, mobile clients on
 * flaky networks - forces this on you.
 *
 * <p><b>How it works.</b> The client generates a unique key per logical operation (not per attempt)
 * and sends it with every attempt. The server records key to response on first execution and, on
 * seeing the key again, replays the stored response without re-running anything.
 *
 * <p><b>The four details that separate a real answer from a hand-wave</b> - all four are demonstrated
 * below:
 * <ol>
 *   <li><b>Failures must not be cached.</b> Caching an error permanently poisons the key and the
 *       client can never succeed.</li>
 *   <li><b>Concurrent duplicates must collapse to one execution</b>, not two. Two retries arriving
 *       together is the normal case, not the edge case.</li>
 *   <li><b>Same key with a different payload is a client bug</b> and must be rejected, not silently
 *       served the old response. Stripe returns 422 for exactly this.</li>
 *   <li><b>Keys expire.</b> Storing them forever is an unbounded, permanently growing table.</li>
 * </ol>
 *
 * <p><b>The design answer that beats all of this, when it is available:</b> make the operation
 * naturally idempotent. {@code SET balance = 100} is idempotent; {@code balance += 10} is not.
 * A unique constraint on {@code (customer_id, idempotency_key)} in the database pushes the whole
 * problem down to a layer that already solves it correctly and survives a server restart - which
 * the in-memory map below does not.
 */
class IdempotencyConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    IdempotencyConflictException(String message) {
        super(message);
    }
}

class IdempotentExecutor {

    /** What happened the first time, plus when, so it can be aged out. */
    private record Entry(Object response, String fingerprint, Instant storedAt) {
    }

    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration retention;
    private final Clock clock;
    private final AtomicInteger executions = new AtomicInteger();

    IdempotentExecutor(Duration retention, Clock clock) {
        this.retention = retention;
        this.clock = clock;
    }

    /**
     * Runs {@code action} at most once per key.
     *
     * @param key         the client-supplied idempotency key
     * @param fingerprint a hash of the request body, so a reused key with different content is caught
     */
    @SuppressWarnings("unchecked")
    <T> T execute(String key, String fingerprint, Supplier<T> action) {
        purgeExpired();

        Entry existing = entries.get(key);
        if (existing != null && !existing.fingerprint().equals(fingerprint)) {
            // Detail 3. Serving the cached response here would be worse than failing: the client
            // believes their SECOND, different request succeeded when it was never executed.
            throw new IdempotencyConflictException(
                    "idempotency key '" + key + "' was already used with a different request body");
        }

        // computeIfAbsent is doing real work here, and it is worth naming why:
        //  - it holds the bin lock for this key, so 16 concurrent retries produce ONE execution
        //    (detail 2) rather than 16 threads all seeing "absent" and all charging the card;
        //  - if the mapping function THROWS, no mapping is recorded, so failures are not cached
        //    (detail 1) and the client's next retry gets a real attempt.
        // The cost: a slow action holds that bin lock, briefly blocking unrelated keys that hash to
        // the same bin. At real scale you would use a per-key lock, or better, a database unique
        // constraint that also survives a restart.
        Entry entry = entries.computeIfAbsent(key, k -> {
            executions.incrementAndGet();
            return new Entry(action.get(), fingerprint, clock.instant());
        });
        return (T) entry.response();
    }

    /** Detail 4: keys are evidence of a recent request, not a permanent record. */
    private void purgeExpired() {
        Instant cutoff = clock.instant().minus(retention);
        entries.entrySet().removeIf(e -> e.getValue().storedAt().isBefore(cutoff));
    }

    int executions() {
        return executions.get();
    }

    int storedKeys() {
        return entries.size();
    }
}

/** The thing we must not do twice. */
class PaymentGateway {

    private final AtomicInteger charges = new AtomicInteger();
    boolean failing;

    String charge(String customer, int amount) {
        if (failing) {
            throw new IllegalStateException("gateway unavailable");
        }
        int n = charges.incrementAndGet();
        return "txn-" + n + " charged " + amount + " to " + customer;
    }

    int chargeCount() {
        return charges.get();
    }
}

public class IdempotencyDemo {

    public static void main(String[] args) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T12:00:00Z"));
        PaymentGateway gateway = new PaymentGateway();
        IdempotentExecutor executor = new IdempotentExecutor(Duration.ofHours(24), clock);

        section("1. Without a key, a retry is a second charge");
        gateway.charge("priya", 500);
        gateway.charge("priya", 500); // the client retried after a timeout
        System.out.println("  charges on the gateway: " + gateway.chargeCount() + "  <-- the customer paid twice");

        section("2. With a key, four retries are one charge");
        PaymentGateway safe = new PaymentGateway();
        IdempotentExecutor guarded = new IdempotentExecutor(Duration.ofHours(24), clock);
        String key = "req-8f2a";
        String body = fingerprint("priya", 500);

        for (int attempt = 1; attempt <= 4; attempt++) {
            String response = guarded.execute(key, body, () -> safe.charge("priya", 500));
            System.out.printf("  attempt %d -> %s%n", attempt, response);
        }
        System.out.println("  actual gateway charges : " + safe.chargeCount() + "  (expected 1)");
        System.out.println("  Every attempt got the SAME transaction id. The client cannot tell which");
        System.out.println("  attempt did the work, and does not need to.");

        section("3. A different key is a different operation");
        guarded.execute("req-91bd", fingerprint("priya", 500), () -> safe.charge("priya", 500));
        System.out.println("  gateway charges : " + safe.chargeCount());
        System.out.println("  The key identifies the OPERATION, not the request. A customer genuinely");
        System.out.println("  buying the same thing twice must generate a new key - that is the client's job.");

        section("4. Reusing a key with a different body is a client bug, not a replay");
        try {
            guarded.execute(key, fingerprint("priya", 9999), () -> safe.charge("priya", 9999));
            System.out.println("  UNEXPECTED: the mismatched request was accepted");
        } catch (IdempotencyConflictException expected) {
            System.out.println("      422 - " + expected.getMessage());
        }
        System.out.println("  Silently replaying the old response would tell the client their 9999");
        System.out.println("  charge succeeded when it was never attempted. Failing loudly is correct.");

        section("5. Failures are NOT cached");
        PaymentGateway flaky = new PaymentGateway();
        IdempotentExecutor retryable = new IdempotentExecutor(Duration.ofHours(24), clock);
        flaky.failing = true;
        try {
            retryable.execute("req-cc01", body, () -> flaky.charge("sam", 200));
        } catch (IllegalStateException expected) {
            System.out.println("      attempt 1 failed - " + expected.getMessage());
        }
        System.out.println("  stored keys after the failure : " + retryable.storedKeys() + " (nothing was cached)");
        flaky.failing = false;
        System.out.println("      attempt 2 -> " + retryable.execute("req-cc01", body, () -> flaky.charge("sam", 200)));
        System.out.println("  Caching the error would poison the key permanently and the client could");
        System.out.println("  never succeed, no matter how many times they retried.");

        section("6. Concurrent duplicates collapse to one execution");
        PaymentGateway raced = new PaymentGateway();
        IdempotentExecutor concurrent = new IdempotentExecutor(Duration.ofHours(24), clock);
        CountDownLatch startGun = new CountDownLatch(1);
        ConcurrentHashMap<String, Integer> responses = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(16);

        for (int i = 0; i < 16; i++) {
            pool.submit(() -> {
                try {
                    startGun.await();
                    String response = concurrent.execute("req-race", body, () -> {
                        sleepBriefly(); // widen the window a real implementation would race in
                        return raced.charge("rahul", 750);
                    });
                    responses.merge(response, 1, Integer::sum);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        startGun.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("  16 simultaneous retries of the same key");
        System.out.println("  distinct responses returned : " + responses.size() + "  (expected 1)");
        System.out.println("  gateway charges             : " + raced.chargeCount() + "  (expected 1)");
        System.out.println("  " + responses);
        System.out.println("  Two retries arriving at once is the NORMAL case. A check-then-act");
        System.out.println("  implementation (contains? then put) charges the card twice here.");

        section("7. Keys expire");
        System.out.println("  stored keys now       : " + guarded.storedKeys());
        clock.advance(Duration.ofHours(25));
        guarded.execute("req-new", fingerprint("meera", 100), () -> safe.charge("meera", 100));
        System.out.println("  after +25h and 1 call : " + guarded.storedKeys() + "  (older keys aged out)");
        System.out.println("  Retention has to outlive the client's retry window - 24h is the usual");
        System.out.println("  choice. Keeping them forever is an unbounded, permanently growing table.");

        section("8. What to say last");
        System.out.println("  This map is per-process and dies with the JVM, so two servers do not share");
        System.out.println("  it and a restart forgets everything. In production the key belongs in the");
        System.out.println("  database, ideally as a UNIQUE constraint on (customer_id, key) inside the");
        System.out.println("  same transaction as the charge - then the database enforces exactly-once");
        System.out.println("  for you, across every server, across restarts.");

        System.out.println("\nDone.");
    }

    /** Stands in for a hash of the request body. */
    private static String fingerprint(String customer, int amount) {
        return customer + ":" + amount;
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
