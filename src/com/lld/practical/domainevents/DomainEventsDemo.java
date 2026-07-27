package com.lld.practical.domainevents;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * DOMAIN EVENTS + EVENT BUS — announce that something happened, and let anyone who cares react.
 *
 * <p><b>How this differs from plain Observer</b>, which is the first thing to say. In Observer the
 * subject owns a list of observers, so the subject still knows that observers exist and observers
 * must know the subject to register with it. An event bus removes even that: the publisher knows
 * only the <em>event type</em>, and so does the subscriber. They never meet. That is what lets you
 * add a fraud-detection subscriber to order placement without opening {@code OrderService} at all.
 *
 * <p><b>Why interviewers push you here.</b> The symptom is a service method that has grown a tail:
 * <pre>
 *   placeOrder(...) {
 *       save(order);
 *       emailService.sendConfirmation(order);   // \
 *       inventory.reserve(order);               //  |  none of this is "placing an order"
 *       analytics.track(order);                 //  |  but all of it lives here
 *       loyalty.awardPoints(order);             // /
 *   }
 * </pre>
 * Every new feature edits this method, it now depends on four collaborators it does not conceptually
 * need, and testing "place an order" requires stubbing all four. Publishing one
 * {@code OrderPlaced} event replaces the tail.
 *
 * <p><b>The two rules that make this safe</b>, and both are commonly missed:
 * <ol>
 *   <li><b>Publish after the transaction commits, never during.</b> Otherwise you email a customer
 *       about an order that then gets rolled back. The aggregate <em>records</em> events; the
 *       application service <em>publishes</em> them once the write succeeded.</li>
 *   <li><b>One handler's failure must not break the others or the publisher.</b> If the analytics
 *       handler throws, the confirmation email must still go out and {@code placeOrder} must still
 *       return successfully.</li>
 * </ol>
 */
interface DomainEvent {
    Instant occurredAt();
}

record OrderPlaced(String orderId, String customerId, BigDecimal total, Instant occurredAt) implements DomainEvent {
}

record OrderCancelled(String orderId, String reason, Instant occurredAt) implements DomainEvent {
}

record PaymentFailed(String orderId, String code, Instant occurredAt) implements DomainEvent {
}

class EventBus {

    // LinkedHashMap + List: subscribers fire in registration order. Not a guarantee you should rely
    // on - if two handlers must run in a fixed order, they are one handler, or one should trigger
    // the other with its own event.
    //
    // The value type is the raw-ish Consumer<?> rather than Consumer<? extends DomainEvent>: a
    // wildcard capture cannot be added to a list of a DIFFERENT capture of the same wildcard. The
    // type safety lives in subscribe()'s signature, not in the field's declaration.
    private final Map<Class<? extends DomainEvent>, List<Consumer<?>>> handlers = new LinkedHashMap<>();

    private final List<String> failures = new ArrayList<>();

    <T extends DomainEvent> void subscribe(Class<T> eventType, Consumer<? super T> handler) {
        handlers.computeIfAbsent(eventType, key -> new ArrayList<>()).add(handler);
    }

    /**
     * Delivers the event to every handler registered for its exact type.
     *
     * <p>The cast is unchecked but provably safe: {@link #subscribe} is the only thing that ever
     * writes to this map, and it ties {@code Class<T>} to {@code Consumer<? super T>} at the call
     * site. This is the standard "heterogeneous typesafe container" idiom - the compiler cannot see
     * the invariant, but one method enforces it.
     *
     * <p>Note the try/catch <b>per handler</b>. Wrapping the whole loop instead would mean handler
     * one throwing silently cancels handlers two and three, which is a bug that shows up months
     * later as "we stopped getting analytics on Tuesdays".
     */
    @SuppressWarnings("unchecked")
    <T extends DomainEvent> void publish(T event) {
        for (Consumer<?> raw : handlers.getOrDefault(event.getClass(), List.of())) {
            Consumer<T> handler = (Consumer<T>) raw;
            try {
                handler.accept(event);
            } catch (RuntimeException handlerFailure) {
                // Isolate, record, continue. In production this goes to a retry queue with the event
                // payload, so the failed side effect can be replayed rather than lost.
                failures.add(event.getClass().getSimpleName() + " -> " + handlerFailure.getMessage());
            }
        }
    }

    List<String> failures() {
        return List.copyOf(failures);
    }

    int subscriberCount(Class<? extends DomainEvent> eventType) {
        return handlers.getOrDefault(eventType, List.of()).size();
    }}

/**
 * The aggregate records events; it does not publish them.
 *
 * <p>This split is the whole trick. The domain object stays free of infrastructure - no bus, no
 * mailer, no repository - and remains a pure unit-testable object. The application service decides
 * when it is safe to let the outside world know.
 */
class Order {

    private final String id;
    private final String customerId;
    private final BigDecimal total;
    private final List<DomainEvent> recordedEvents = new ArrayList<>();
    private boolean cancelled;

    Order(String id, String customerId, BigDecimal total) {
        this.id = id;
        this.customerId = customerId;
        this.total = total;
        recordedEvents.add(new OrderPlaced(id, customerId, total, Instant.parse("2026-07-27T10:00:00Z")));
    }

    void cancel(String reason) {
        if (cancelled) {
            throw new IllegalStateException("order " + id + " is already cancelled");
        }
        cancelled = true;
        recordedEvents.add(new OrderCancelled(id, reason, Instant.parse("2026-07-27T10:05:00Z")));
    }

    /** Hand over the events and forget them, so a second flush cannot double-publish. */
    List<DomainEvent> releaseEvents() {
        List<DomainEvent> released = List.copyOf(recordedEvents);
        recordedEvents.clear();
        return released;
    }

    int pendingEventCount() {
        return recordedEvents.size();
    }

    String id() {
        return id;
    }
}

public class DomainEventsDemo {

    public static void main(String[] args) {
        EventBus bus = new EventBus();
        List<String> emails = new ArrayList<>();
        List<String> reservations = new ArrayList<>();
        List<String> analytics = new ArrayList<>();

        section("1. One event, several independent reactions");
        bus.subscribe(OrderPlaced.class, e -> emails.add("confirmation to " + e.customerId()));
        bus.subscribe(OrderPlaced.class, e -> reservations.add("reserve stock for " + e.orderId()));
        bus.subscribe(OrderPlaced.class, e -> analytics.add("revenue +" + e.total()));
        bus.subscribe(OrderCancelled.class, e -> emails.add("cancellation notice for " + e.orderId()));

        bus.publish(new OrderPlaced("O-1", "priya", new BigDecimal("499.00"), Instant.parse("2026-07-27T10:00:00Z")));
        System.out.println("  emails       : " + emails);
        System.out.println("  reservations : " + reservations);
        System.out.println("  analytics    : " + analytics);
        System.out.println("  OrderService did not import a mailer, an inventory client or an analytics SDK.");

        section("2. Routing is by type - no handler sees an event it did not ask for");
        emails.clear();
        reservations.clear();
        bus.publish(new OrderCancelled("O-1", "customer changed their mind", Instant.parse("2026-07-27T10:05:00Z")));
        System.out.println("  emails       : " + emails);
        System.out.println("  reservations : " + reservations + "   (stock handler never fired)");
        bus.publish(new PaymentFailed("O-1", "CARD_DECLINED", Instant.parse("2026-07-27T10:06:00Z")));
        System.out.println("  PaymentFailed has " + bus.subscriberCount(PaymentFailed.class)
                + " subscribers - publishing it is a harmless no-op, not a crash.");

        section("3. A broken handler must not take down the others");
        EventBus resilient = new EventBus();
        List<String> ran = new ArrayList<>();
        resilient.subscribe(OrderPlaced.class, e -> ran.add("email"));
        resilient.subscribe(OrderPlaced.class, e -> {
            throw new IllegalStateException("analytics service unreachable");
        });
        resilient.subscribe(OrderPlaced.class, e -> ran.add("loyalty points"));

        resilient.publish(new OrderPlaced("O-2", "sam", new BigDecimal("120.00"), Instant.parse("2026-07-27T11:00:00Z")));
        System.out.println("  handlers that completed : " + ran);
        System.out.println("  isolated failures       : " + resilient.failures());
        System.out.println("  placeOrder() still returned successfully. Analytics being down is not a");
        System.out.println("  reason to refuse the customer's order.");

        section("4. Record in the aggregate, publish AFTER the commit");
        EventBus afterCommit = new EventBus();
        List<String> sent = new ArrayList<>();
        afterCommit.subscribe(OrderPlaced.class, e -> sent.add("email for " + e.orderId()));

        Order order = new Order("O-3", "rahul", new BigDecimal("899.00"));
        System.out.println("  order created, events recorded : " + order.pendingEventCount());
        System.out.println("  emails sent so far             : " + sent.size());
        System.out.println("  ...the database write fails, transaction rolls back...");
        System.out.println("  emails sent                    : " + sent.size() + "  (nobody was told about an order that does not exist)");

        System.out.println();
        Order committed = new Order("O-4", "meera", new BigDecimal("249.00"));
        System.out.println("  second order, this time the commit succeeds - now we drain and publish:");
        committed.releaseEvents().forEach(event -> afterCommit.publish((OrderPlaced) event));
        System.out.println("  emails sent                    : " + sent);
        System.out.println("  events left on the aggregate   : " + committed.pendingEventCount()
                + "   (released, so a second flush cannot double-send)");

        section("5. Adding a subscriber costs zero edits to the publisher");
        List<String> fraud = new ArrayList<>();
        bus.subscribe(OrderPlaced.class, e -> {
            if (e.total().compareTo(new BigDecimal("1000")) > 0) {
                fraud.add("review " + e.orderId());
            }
        });
        bus.publish(new OrderPlaced("O-5", "vikram", new BigDecimal("4999.00"), Instant.parse("2026-07-27T12:00:00Z")));
        System.out.println("  fraud queue : " + fraud);
        System.out.println("  A whole new feature, and OrderService was not opened. That is Open/Closed");
        System.out.println("  at the level of a whole subsystem rather than a single class.");

        section("6. Synchronous here - know when to go asynchronous");
        System.out.println("  This bus dispatches on the caller's thread, so a slow handler slows the");
        System.out.println("  request. That is fine for cheap in-process reactions and much easier to");
        System.out.println("  debug. Move to a queue (or an ExecutorService) when handlers do I/O -");
        System.out.println("  and accept what that costs: ordering guarantees, at-least-once delivery,");
        System.out.println("  and handlers that must be idempotent because they WILL be re-run.");

        System.out.println("\nDone.");
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
