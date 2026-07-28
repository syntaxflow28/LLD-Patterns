package problems.booking;

import java.util.List;
import java.util.Optional;

/**
 * THE CRUX OF THIS PROBLEM — a temporary, exclusive, self-expiring hold on seats.
 *
 * <p>Everything else in a booking system is CRUD. This interface is the part interviewers are
 * actually testing, and the question that gets you there is always some version of <em>"two users
 * click the same seat at the same moment — what happens?"</em>
 *
 * <p><b>Why a lock and not just a flag.</b> Consider the two obvious designs:
 * <ul>
 *   <li><b>Mark booked at seat selection.</b> The user closes the tab, and seat A5 is dead for the
 *       rest of the show. Cinemas will not accept that.</li>
 *   <li><b>Mark booked at payment success.</b> Both users pass the availability check, both pay, one
 *       gets a refund and a one-star review. This is a textbook check-then-act race.</li>
 * </ul>
 * A hold with a TTL is the only design that is both exclusive and recoverable. Say "I'd hold the
 * seats with a short TTL rather than booking them at selection" and you have answered the hard part
 * of this question in one sentence.
 *
 * <p><b>Why the TTL is not optional.</b> A lock with no expiry needs someone to unlock it. If the
 * user's browser dies mid-checkout, nobody will — and the seat is stranded until an operator
 * intervenes. Self-expiry means a crashed client cannot permanently damage inventory. This is the
 * same reasoning behind lease-based distributed locks.
 *
 * <p><b>Why locking is all-or-nothing.</b> A family wants four seats together. Acquiring three and
 * failing on the fourth must release all three — otherwise a burst of failed attempts leaves the
 * auditorium peppered with orphaned holds and nobody can book anything.
 *
 * <p><b>The distributed follow-up.</b> With several booking servers this becomes Redis
 * {@code SET key value NX PX 300000} (set-if-absent with expiry), or a database row with
 * {@code SELECT ... FOR UPDATE}. The interface does not change — which is the point of having one.
 */
public interface SeatLockProvider {

    /** Thrown when seats cannot be held. Checked-style intent, unchecked ergonomics. */
    class SeatUnavailableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        /** transient because List is not Serializable; these are never sent over the wire anyway. */
        private final transient List<String> seatIds;

        public SeatUnavailableException(String message, List<String> seatIds) {
            super(message);
            this.seatIds = List.copyOf(seatIds);
        }

        public List<String> seatIds() {
            return seatIds;
        }
    }

    /**
     * Holds every listed seat for {@code userId}, or holds none of them.
     *
     * @throws SeatUnavailableException if any seat is already held by someone else
     */
    void lockSeats(String showId, List<String> seatIds, String userId);

    /** Releases holds owned by {@code userId}. Holds owned by others are left alone. */
    void unlockSeats(String showId, List<String> seatIds, String userId);

    /** True if the seat is currently held by anyone, expired holds excluded. */
    boolean isLocked(String showId, String seatId);

    /** Who holds this seat right now, if anyone. Used to validate a payment against its hold. */
    Optional<String> lockedBy(String showId, String seatId);

    /** True only if every listed seat is held by exactly this user. Checked before confirming. */
    boolean holdsAll(String showId, List<String> seatIds, String userId);
}
