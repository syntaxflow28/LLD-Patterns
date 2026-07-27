package com.lld.practical.di;

/**
 * DEPENDENCY INJECTION — hand a class its collaborators from the outside instead of letting it
 * construct them. This is how you actually *implement* the Dependency Inversion Principle.
 *
 * Three flavours: constructor injection (preferred), setter injection, interface injection.
 * Constructor injection wins because it makes dependencies explicit and allows final fields.
 *
 * When to use in LLD:
 *   - Everywhere. In an interview, wiring dependencies in main()/a factory (rather than `new`-ing
 *     them inside services) is a strong signal — it shows you care about testability.
 */

interface MessageSender { void send(String to, String body); }
interface AuditLog      { void record(String event); }

class EmailSender implements MessageSender {
    public void send(String to, String body) { System.out.println("EMAIL -> " + to + ": " + body); }
}

class SmsSender implements MessageSender {
    public void send(String to, String body) { System.out.println("SMS -> " + to + ": " + body); }
}

class ConsoleAuditLog implements AuditLog {
    public void record(String event) { System.out.println("AUDIT: " + event); }
}

/** A fake used in tests — possible only because dependencies are injected. */
class FakeMessageSender implements MessageSender {
    int callCount = 0;
    public void send(String to, String body) { callCount++; }
}

/**
 * BAD (for contrast):
 *   class OrderNotifier { private final EmailSender sender = new EmailSender(); }
 *   -> welded to email, impossible to unit test without sending real mail.
 */
class OrderNotifier {
    private final MessageSender sender;   // abstraction, not a concrete class
    private final AuditLog audit;

    /** Constructor injection: dependencies are explicit, required, and final. */
    OrderNotifier(MessageSender sender, AuditLog audit) {
        this.sender = sender;
        this.audit = audit;
    }

    void orderShipped(String customer, String orderId) {
        sender.send(customer, "Order " + orderId + " has shipped");
        audit.record("shipped:" + orderId);
    }
}

public class DependencyInjectionDemo {
    public static void main(String[] args) {
        AuditLog audit = new ConsoleAuditLog();

        // The composition root: the ONE place that knows about concrete classes.
        new OrderNotifier(new EmailSender(), audit).orderShipped("alice@x.com", "A-1");
        new OrderNotifier(new SmsSender(), audit).orderShipped("+911234567890", "A-2");

        // Testing becomes trivial: inject a fake, assert on it. No network, no mocking framework.
        FakeMessageSender fake = new FakeMessageSender();
        new OrderNotifier(fake, audit).orderShipped("test@x.com", "A-3");
        System.out.println("Fake sender invoked " + fake.callCount + " time(s)");
    }
}
