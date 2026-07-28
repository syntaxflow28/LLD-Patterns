package problems.notification;

/**
 * The outcome of one delivery attempt sequence.
 *
 * <p><b>Why a result object instead of {@code void} or {@code boolean}.</b> Notification delivery is
 * a network call to a third party: it fails, it fails for interestingly different reasons, and the
 * caller usually wants to record why. {@code void} throws that away. {@code boolean} keeps one bit
 * and loses the reason, the channel and the attempt count — exactly the three things you need when
 * someone asks "why didn't the customer get their OTP?".
 *
 * <p><b>Why not throw on failure.</b> A failed notification is an expected outcome, not an
 * exceptional one. Exceptions for routine control flow force try/catch at every call site and make
 * bulk sends ("notify these 500 users") awkward — one bad phone number should not abort the batch.
 * Reserve exceptions for programmer errors; return values for business outcomes.
 */
public record DeliveryResult(
        String channel,
        String recipientId,
        boolean delivered,
        int attempts,
        String detail) {

    public static DeliveryResult ok(String channel, String recipientId, int attempts) {
        return new DeliveryResult(channel, recipientId, true, attempts, "delivered");
    }

    public static DeliveryResult failed(String channel, String recipientId, int attempts, String reason) {
        return new DeliveryResult(channel, recipientId, false, attempts, reason);
    }

    /** Not reachable at all - distinct from "tried and failed", and worth not retrying. */
    public static DeliveryResult unreachable(String channel, String recipientId, String reason) {
        return new DeliveryResult(channel, recipientId, false, 0, reason);
    }

    @Override
    public String toString() {
        return String.format("%-6s %-6s %s  attempts=%d  %s",
                channel, delivered ? "OK" : "FAIL", recipientId, attempts, detail);
    }
}
