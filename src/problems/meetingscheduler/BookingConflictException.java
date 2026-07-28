package problems.meetingscheduler;

/**
 * Thrown when a requested slot is already taken.
 *
 * <p>It carries the id of the meeting that blocked you, not just a message. "Room B is busy" sends
 * the user hunting; "Room B is held by mtg-4 until 11:00" lets them decide immediately. Putting the
 * blocking meeting on the exception is nearly free at the throw site and is the difference between an
 * error and an answer.
 */
public class BookingConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String conflictingMeetingId;

    public BookingConflictException(String message, String conflictingMeetingId) {
        super(message);
        this.conflictingMeetingId = conflictingMeetingId;
    }

    public String conflictingMeetingId() {
        return conflictingMeetingId;
    }
}
