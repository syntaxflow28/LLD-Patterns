package com.lld.problems.meetingscheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * One room's bookings, and the algorithmic core of the problem: <b>conflict detection in O(log n)</b>.
 *
 * <p><b>The naive answer and why it is rejected.</b> Keep a {@code List<Meeting>} and, before
 * booking, loop over it asking "does this overlap?". It is correct, it is three lines, and it is
 * O(n) on the write path of a system where a busy room accumulates thousands of meetings a year and
 * every booking screen re-checks availability on every keystroke.
 *
 * <p><b>The insight:</b> if bookings are kept sorted by start time and no two of them overlap, then
 * only <b>two</b> meetings can possibly conflict with a new {@code [start, end)}:
 * <ul>
 *   <li>the last meeting starting at or before {@code start} — {@code floorEntry(start)}. It
 *       conflicts if it has not finished yet, i.e. its end is after {@code start}.</li>
 *   <li>the first meeting starting at or after {@code start} — {@code ceilingEntry(start)}. It
 *       conflicts if it begins before {@code end}.</li>
 * </ul>
 * Nothing else can reach across, because any meeting further away would have to overlap one of those
 * two, and the invariant says they do not overlap each other. Two {@code TreeMap} lookups, O(log n),
 * no iteration at all.
 *
 * <p>That invariant — <em>the meetings in this map never overlap</em> — is what buys the speed, so it
 * has to be enforced on the way in. It is enforced by {@link MeetingScheduler}, which does the check
 * and the insert under one lock; {@link #add} deliberately trusts its caller rather than re-checking,
 * because a check inside {@code add} would be a second, separate check-then-act race rather than a
 * fix for the first one.
 */
public final class RoomCalendar {

    private final String roomId;

    /** Sorted by start instant. The no-overlap invariant is what makes two probes sufficient. */
    private final NavigableMap<Instant, Meeting> byStart = new TreeMap<>();

    /** So cancellation is O(log n) rather than a scan for the id. */
    private final Map<String, Instant> startByMeetingId = new HashMap<>();

    public RoomCalendar(String roomId) {
        this.roomId = Objects.requireNonNull(roomId, "roomId");
    }

    /** @return the meeting that blocks {@code [start, end)}, or empty if the slot is free */
    public Optional<Meeting> findConflict(Instant start, Instant end) {
        Map.Entry<Instant, Meeting> earlier = byStart.floorEntry(start);
        if (earlier != null && earlier.getValue().end().isAfter(start)) {
            return Optional.of(earlier.getValue());
        }
        Map.Entry<Instant, Meeting> later = byStart.ceilingEntry(start);
        if (later != null && later.getKey().isBefore(end)) {
            return Optional.of(later.getValue());
        }
        return Optional.empty();
    }

    public boolean isFree(Instant start, Instant end) {
        return findConflict(start, end).isEmpty();
    }

    /** Caller must have verified the slot is free while holding this calendar's lock. */
    public void add(Meeting meeting) {
        byStart.put(meeting.start(), meeting);
        startByMeetingId.put(meeting.id(), meeting.start());
    }

    public Optional<Meeting> cancel(String meetingId) {
        Instant start = startByMeetingId.remove(meetingId);
        return start == null ? Optional.empty() : Optional.ofNullable(byStart.remove(start));
    }

    /**
     * The earliest instant at or after {@code from} where {@code duration} fits.
     *
     * <p>The loop is the whole trick and it is three lines: try the cursor, and if something is in
     * the way, jump the cursor to the end of <em>that</em> meeting rather than nudging forward by a
     * minute. Stepping forward in fixed increments is the version that looks fine in a demo and takes
     * 480 probes to cross a working day.
     *
     * <p>It terminates because a blocking meeting must end strictly after the cursor, so the cursor
     * strictly increases every iteration, and it runs at most once per meeting in the way.
     */
    public Instant earliestSlotFrom(Instant from, Duration duration) {
        Instant cursor = from;
        while (true) {
            Optional<Meeting> blocker = findConflict(cursor, cursor.plus(duration));
            if (blocker.isEmpty()) {
                return cursor;
            }
            cursor = blocker.get().end();
        }
    }

    public List<Meeting> schedule() {
        return new ArrayList<>(byStart.values());
    }

    public int size() {
        return byStart.size();
    }

    public String roomId() {
        return roomId;
    }
}
