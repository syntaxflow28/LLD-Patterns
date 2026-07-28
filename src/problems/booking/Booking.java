package problems.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * One user's attempt to buy specific seats for one show.
 *
 * <p><b>Why this is a class with a mutable status, when almost everything else in this repo is a
 * record.</b> A booking genuinely has identity and a lifecycle: the same booking that was pending at
 * 19:02 is confirmed at 19:03, and the user's reference number must not change. Modelling it as an
 * immutable value would mean replacing it in the map on every transition, and any code holding the
 * old reference would silently observe a stale status. This is the standard entity-versus-value
 * distinction, and it is worth naming out loud: <em>seats are values, bookings are entities</em>.
 *
 * <p><b>Why the total is stored rather than recomputed.</b> Price is agreed at booking time. If the
 * surge multiplier changes while the user is on the payment page, they must still pay the number
 * they were shown. Recomputing on read would silently re-price confirmed orders — a real bug in
 * real systems, and an easy one to defend against.
 *
 * <p><b>Why {@code expiresAt} lives here as well as in the lock.</b> The lock enforces exclusivity;
 * this field is what the user is shown ("complete payment within 4:59"). They are two views of the
 * same deadline, and keeping the booking's copy makes the API usable without exposing the lock.
 */
public final class Booking {

    private final String id;
    private final String showId;
    private final String userId;
    private final List<String> seatIds;
    private final BigDecimal totalAmount;
    private final Instant createdAt;
    private final Instant expiresAt;

    private BookingStatus status;

    Booking(String id, String showId, String userId, List<String> seatIds,
            BigDecimal totalAmount, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.showId = showId;
        this.userId = userId;
        this.seatIds = List.copyOf(seatIds);
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.status = BookingStatus.PENDING_PAYMENT;
    }

    /**
     * Package-private: only {@link BookingService} drives transitions, and only while holding its
     * lock. A public setter would let a caller mark a booking CONFIRMED without paying — the kind of
     * encapsulation slip interviewers notice immediately.
     */
    void transitionTo(BookingStatus next) {
        this.status = next;
    }

    public String id() {
        return id;
    }

    public String showId() {
        return showId;
    }

    public String userId() {
        return userId;
    }

    public List<String> seatIds() {
        return seatIds;
    }

    public BigDecimal totalAmount() {
        return totalAmount;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public BookingStatus status() {
        return status;
    }

    @Override
    public String toString() {
        return String.format("%s %-15s %s seats=%s total=%s",
                id, status, userId, seatIds, totalAmount);
    }
}
