package problems.meetingscheduler;

import java.time.Instant;

/**
 * A booked slot.
 *
 * <p><b>The single most important decision in this problem is on this class</b>, and it is one line:
 * intervals are <b>half-open</b>, {@code [start, end)}. The meeting occupies its start instant and
 * does <em>not</em> occupy its end instant.
 *
 * <p>Get that wrong and a 10:00-11:00 meeting conflicts with an 11:00-12:00 meeting, which means
 * nobody in the building can ever book back-to-back. It is the first bug an interviewer looks for and
 * the first thing a real user reports. Half-open intervals are also why the overlap test below has no
 * {@code equals} anywhere in it — every boundary case falls out of the strict comparisons.
 *
 * @param id        stable identifier, so cancellation does not need to re-describe the meeting
 * @param roomId    the room this occupies
 * @param organizer who booked it
 * @param start     inclusive
 * @param end       exclusive
 * @param attendees headcount, used to pick a room that actually fits
 */
public record Meeting(String id, String roomId, String organizer, Instant start, Instant end, int attendees) {

    public Meeting {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end are required");
        }
        if (!start.isBefore(end)) {
            // Zero-length and inverted meetings both land here. A zero-length meeting sounds
            // harmless until it silently matches nothing in a half-open overlap test and becomes a
            // ghost booking nobody can find or cancel.
            throw new IllegalArgumentException("start must be strictly before end: " + start + " .. " + end);
        }
        if (attendees < 1) {
            throw new IllegalArgumentException("a meeting needs at least one attendee");
        }
    }

    /**
     * The overlap predicate, and the sentence to say out loud in the interview:
     * <b>"two half-open intervals overlap when each starts before the other ends."</b>
     *
     * <p>Two strict comparisons, no special cases, no {@code ||} chain of four boundary conditions.
     * Candidates who enumerate "case 1: A starts inside B, case 2: B starts inside A, case 3: A
     * contains B..." usually miss one and always take longer.
     */
    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    public boolean overlaps(Meeting other) {
        return overlaps(other.start, other.end);
    }
}
