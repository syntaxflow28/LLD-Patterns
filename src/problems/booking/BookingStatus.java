package problems.booking;

/**
 * The lifecycle of a booking. Small enum, but the states are the design.
 *
 * <pre>
 *   PENDING_PAYMENT --pay--&gt; CONFIRMED
 *          |                     |
 *       timeout               refund
 *          v                     v
 *       EXPIRED              CANCELLED
 * </pre>
 *
 * <p><b>Why {@code PENDING_PAYMENT} has to exist.</b> The naive model is booked or not booked, which
 * forces you to choose between two broken behaviours: mark the seat booked before payment (and lose
 * it forever when the user abandons the checkout page) or after payment (and let two users both
 * reach the payment screen for the same seat, one of whom gets a refund and a bad review). The
 * pending state with a deadline is what makes the seat unavailable to others <em>and</em>
 * recoverable if payment never arrives.
 *
 * <p><b>Why {@code EXPIRED} and {@code CANCELLED} are separate.</b> They look the same to the seat
 * map — both release it — but they are completely different to finance and analytics. Cancelled
 * means money moved and must move back; expired means it never moved. Collapsing them loses the
 * refund obligation.
 */
public enum BookingStatus {

    /** Seats are held; the clock is running. */
    PENDING_PAYMENT,

    /** Paid. Seats are now permanently allocated for this show. */
    CONFIRMED,

    /** The hold ran out before payment. Seats were returned to the pool; no money changed hands. */
    EXPIRED,

    /** Deliberately cancelled after confirmation. Seats returned, refund owed. */
    CANCELLED
}
