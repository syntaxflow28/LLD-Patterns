package com.lld.problems.meetingscheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FACADE — the one class callers use, and the only place that knows about locking.
 *
 * <p><b>The race this class exists to close.</b> "Is the room free?" followed by "book it" is a
 * textbook check-then-act: two people looking at the same free 10:00 slot both see it free, both
 * book, and the loser never finds out. It is the double-booking bug, it is the thing the interviewer
 * will ask about, and it cannot be fixed inside {@link RoomCalendar} — a thread-safe calendar with
 * two individually-atomic methods still has the gap <em>between</em> them.
 *
 * <p>The fix is to make check-and-insert one atomic step, and the granularity matters: locking
 * <em>per room</em> rather than globally means booking the 3rd-floor huddle room never blocks someone
 * booking the boardroom. A single global lock is also correct and much easier to defend when you are
 * short on time; say which trade you are making.
 *
 * <p><b>Why picking the room is a policy, not an algorithm.</b> {@link #book} takes the
 * <em>smallest</em> room that fits. Handing a 2-person stand-up the 50-seat boardroom is technically
 * a successful booking and operationally a disaster, and "best fit, then lowest floor" is the kind of
 * detail that shows you have thought past the happy path. It is also the natural place a
 * <b>Strategy</b> would go if the interviewer asks for pluggable allocation — worth naming, not worth
 * building unprompted.
 */
public final class MeetingScheduler {

    /** A suggested booking that has not been made yet. */
    public record Slot(Room room, Instant start, Instant end) {
    }

    /** Populated at setup, read-only afterwards, so these two need no synchronisation. */
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, RoomCalendar> calendars = new LinkedHashMap<>();

    /**
     * Concurrent on purpose: it is written while holding a <em>room</em> lock, and two threads
     * booking two different rooms hold two different locks. Per-room locking buys parallelism and
     * costs exactly this — every structure shared across rooms has to defend itself.
     */
    private final Map<String, String> roomByMeetingId = new ConcurrentHashMap<>();
    private final AtomicLong meetingIds = new AtomicLong();

    public void addRoom(Room room) {
        Objects.requireNonNull(room, "room");
        rooms.put(room.id(), room);
        calendars.put(room.id(), new RoomCalendar(room.id()));
    }

    /**
     * Books a named room, or fails with the meeting that is in the way.
     *
     * <p>The synchronized block spans the check <em>and</em> the insert. That is the entire fix, and
     * it is worth pointing at explicitly rather than saying "I'd add locking".
     */
    public Meeting bookRoom(String roomId, String organizer, Instant start, Instant end, int attendees) {
        Room room = rooms.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException("no such room: " + roomId);
        }
        if (!room.fits(attendees)) {
            throw new IllegalArgumentException(
                    room.id() + " seats " + room.capacity() + ", which is short for " + attendees);
        }
        RoomCalendar calendar = calendars.get(roomId);

        synchronized (calendar) {
            Optional<Meeting> conflict = calendar.findConflict(start, end);
            if (conflict.isPresent()) {
                Meeting blocker = conflict.get();
                throw new BookingConflictException(
                        roomId + " is held by " + blocker.id() + " until " + blocker.end(), blocker.id());
            }
            Meeting meeting = new Meeting(
                    "mtg-" + meetingIds.incrementAndGet(), roomId, organizer, start, end, attendees);
            calendar.add(meeting);
            roomByMeetingId.put(meeting.id(), roomId);
            return meeting;
        }
    }

    /**
     * Books the smallest room that fits and is free, preferring lower floors on a tie.
     *
     * <p>Each candidate is tried under its own lock, so a room lost to a concurrent booker is simply
     * skipped rather than failing the whole request. That is the behaviour a user expects: they asked
     * for "a room", not for that room.
     */
    public Optional<Meeting> book(String organizer, Instant start, Instant end, int attendees) {
        for (Room room : candidateRooms(attendees)) {
            try {
                return Optional.of(bookRoom(room.id(), organizer, start, end, attendees));
            } catch (BookingConflictException busy) {
                // Expected: someone else has it. Try the next-smallest room.
                continue;
            }
        }
        return Optional.empty();
    }

    public boolean cancel(String meetingId) {
        String roomId = roomByMeetingId.remove(meetingId);
        if (roomId == null) {
            return false;
        }
        RoomCalendar calendar = calendars.get(roomId);
        synchronized (calendar) {
            return calendar.cancel(meetingId).isPresent();
        }
    }

    public List<Room> availableRooms(Instant start, Instant end, int attendees) {
        List<Room> free = new ArrayList<>();
        for (Room room : candidateRooms(attendees)) {
            RoomCalendar calendar = calendars.get(room.id());
            synchronized (calendar) {
                if (calendar.isFree(start, end)) {
                    free.add(room);
                }
            }
        }
        return free;
    }

    /**
     * "When is the next time I can get a room for 30 minutes?"
     *
     * <p>Ask each candidate room for its own earliest opening and keep the best. O(rooms x meetings
     * in the way) and not a minute-by-minute sweep of the calendar — the per-room search jumps over
     * each blocking meeting in one step.
     */
    public Optional<Slot> earliestSlot(Instant from, Duration duration, int attendees) {
        Slot best = null;
        for (Room room : candidateRooms(attendees)) {
            RoomCalendar calendar = calendars.get(room.id());
            Instant start;
            synchronized (calendar) {
                start = calendar.earliestSlotFrom(from, duration);
            }
            if (best == null || start.isBefore(best.start())) {
                best = new Slot(room, start, start.plus(duration));
            }
        }
        return Optional.ofNullable(best);
    }

    public List<Meeting> scheduleFor(String roomId) {
        RoomCalendar calendar = calendars.get(roomId);
        if (calendar == null) {
            throw new IllegalArgumentException("no such room: " + roomId);
        }
        synchronized (calendar) {
            return calendar.schedule();
        }
    }

    /** Smallest room that fits first, then lowest floor. Best fit, not first fit. */
    private List<Room> candidateRooms(int attendees) {
        List<Room> candidates = new ArrayList<>();
        for (Room room : rooms.values()) {
            if (room.fits(attendees)) {
                candidates.add(room);
            }
        }
        candidates.sort(Comparator.comparingInt(Room::capacity).thenComparingInt(Room::floor));
        return candidates;
    }
}
