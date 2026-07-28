package patterns.creational.factory;

/**
 * FACTORY METHOD — define an interface for creating an object, but let a factory decide which
 * concrete class to instantiate. Callers depend on the abstraction, not the concrete type.
 *
 * When to use in LLD:
 *   - You have a family of types selected at runtime (payment methods, notification channels,
 *     parking-spot types) and want to centralize the "which class?" decision (Open/Closed + DIP).
 *
 * Benefit: adding a new type = add a class + one line in the factory; callers don't change.
 */

interface Notification {
    void send(String to, String message);
}

class EmailNotification implements Notification {
    public void send(String to, String message) {
        System.out.println("EMAIL -> " + to + ": " + message);
    }
}

class SmsNotification implements Notification {
    public void send(String to, String message) {
        System.out.println("SMS -> " + to + ": " + message);
    }
}

class PushNotification implements Notification {
    public void send(String to, String message) {
        System.out.println("PUSH -> " + to + ": " + message);
    }
}

enum Channel { EMAIL, SMS, PUSH }

/** The factory: the single place that knows how to build each concrete Notification. */
class NotificationFactory {
    Notification create(Channel channel) {
        return switch (channel) {
            case EMAIL -> new EmailNotification();
            case SMS   -> new SmsNotification();
            case PUSH  -> new PushNotification();
        };
    }
}

public class FactoryDemo {
    public static void main(String[] args) {
        NotificationFactory factory = new NotificationFactory();

        // Caller only knows the Notification interface + the desired channel.
        for (Channel c : Channel.values()) {
            Notification n = factory.create(c);
            n.send("user@example.com", "Your order shipped");
        }
    }
}
