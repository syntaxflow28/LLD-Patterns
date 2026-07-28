package problems.meetingscheduler;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Meeting room scheduler - a 45 minute problem, run end to end.
 *
 * <p>Every section reproduces a failure rather than describing one, including the double booking race
 * and the linear scan this design exists to avoid.
 */
public final class MeetingSchedulerDemo {

    private static final DateTimeFormatter HHMM =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC);

    private static final Instant NINE_AM = Instant.parse("2026-03-02T09:00:00Z");

    private MeetingSchedulerDemo() {
    }

    public static void main(String[] args) throws Exception {
        budget();
        halfOpenIntervals();
        conflicts();
        smallestRoomThatFits();
        cancellation();
        earliestSlot();
        conflictDetectionCost();
        doubleBookingRace();
        scopeNotes();
        System.out.println("\nDone.");
    }

    // ---------------------------------------------------------------- 1

    private static void budget() {
        section("1. The 45 minute budget");
        System.out.println("""
                    0-05  clarify: half-open intervals? recurring? timezones? cancellations?
                    05-10 model:   Room, Meeting(start, end), RoomCalendar, MeetingScheduler
                    10-20 code:    overlap predicate + TreeMap conflict check
                    20-30 code:    book / cancel / availableRooms
                    30-38 code:    earliest available slot
                    38-45 talk:    the double booking race, and what you would build next

                  Ship the overlap check first. It is the part that is graded, and every later
                  method is one call to it.\
                """);
    }

    // ---------------------------------------------------------------- 2

    private static void halfOpenIntervals() {
        section("2. Half-open intervals, or nobody books back-to-back");

        Instant tenToEleven = at(10, 0);
        Instant eleven = at(11, 0);
        Instant twelve = at(12, 0);

        Meeting standup = new Meeting("mtg-x", "Boardroom", "Priya", tenToEleven, eleven, 4);

        System.out.println("  a 10:00-11:00 meeting exists; someone now asks for 11:00-12:00");
        System.out.println("    half-open [start, end) overlap? " + standup.overlaps(eleven, twelve)
                + "   <- correct, they are adjacent, not overlapping");
        System.out.println("    closed    [start, end] overlap? " + closedOverlap(standup, eleven, twelve)
                + "   <- the bug: back-to-back booking is now impossible");

        System.out.println("\n  the other boundary, a meeting that ends exactly when ours starts:");
        System.out.println("    09:00-10:00 vs 10:00-11:00 overlap? "
                + new Meeting("mtg-y", "Boardroom", "Rahul", at(9, 0), tenToEleven, 2)
                        .overlaps(tenToEleven, eleven));

        System.out.println("""

                  Say it out loud once and you will never write the four-case version:
                  'two intervals overlap when each starts before the other ends'.
                    return start.isBefore(otherEnd) && otherStart.isBefore(end);
                  Two strict comparisons. No equals, no special cases, no missed boundary.\
                """);
    }

    /** The wrong version, kept only so section 2 can show it failing. */
    private static boolean closedOverlap(Meeting meeting, Instant otherStart, Instant otherEnd) {
        return !meeting.start().isAfter(otherEnd) && !otherStart.isAfter(meeting.end());
    }

    // ---------------------------------------------------------------- 3

    private static void conflicts() {
        section("3. Booking, and being told exactly what is in the way");

        MeetingScheduler scheduler = office();
        Meeting first = scheduler.bookRoom("Boardroom", "Priya", at(10, 0), at(11, 0), 8);
        System.out.println("  booked " + first.id() + "  Boardroom  "
                + HHMM.format(first.start()) + "-" + HHMM.format(first.end()));

        try {
            scheduler.bookRoom("Boardroom", "Rahul", at(10, 30), at(11, 30), 6);
            System.out.println("  ERROR: the overlapping booking was accepted");
        } catch (BookingConflictException conflict) {
            System.out.println("  rejected 10:30-11:30: " + conflict.getMessage());
            System.out.println("  the exception names the blocker (" + conflict.conflictingMeetingId()
                    + "), so the caller can offer 11:00 instead of just saying 'busy'");
        }

        Meeting adjacent = scheduler.bookRoom("Boardroom", "Rahul", at(11, 0), at(11, 30), 6);
        System.out.println("  accepted 11:00-11:30 as " + adjacent.id() + " - adjacent is not a conflict");
    }

    // ---------------------------------------------------------------- 4

    private static void smallestRoomThatFits() {
        section("4. 'Any room' means the smallest one that fits");

        MeetingScheduler scheduler = office();
        Instant start = at(10, 0);
        Instant end = at(11, 0);

        System.out.println("  rooms free 10:00-11:00 for 2 people: " + scheduler.availableRooms(start, end, 2));

        Optional<Meeting> pairing = scheduler.book("Priya", start, end, 2);
        System.out.println("  2 people  -> " + pairing.map(Meeting::roomId).orElse("nothing free"));

        Optional<Meeting> review = scheduler.book("Rahul", start, end, 5);
        System.out.println("  5 people  -> " + review.map(Meeting::roomId).orElse("nothing free"));

        Optional<Meeting> allHands = scheduler.book("Meera", start, end, 12);
        System.out.println("  12 people -> " + allHands.map(Meeting::roomId).orElse("nothing free"));

        Optional<Meeting> overflow = scheduler.book("Arjun", start, end, 2);
        System.out.println("  2 more    -> " + overflow.map(Meeting::roomId).orElse("nothing free")
                + "   (Huddle and Focus are taken, so it falls through to a bigger room)");

        System.out.println("""

                  First-fit would have burned the Boardroom on the first two-person pairing and
                  then had nowhere to put the all-hands. Best-fit is one comparator and it is the
                  difference between a scheduler people use and one they route around.\
                """);
    }

    // ---------------------------------------------------------------- 5

    private static void cancellation() {
        section("5. Cancelling frees the slot, in O(log n)");

        MeetingScheduler scheduler = office();
        Meeting meeting = scheduler.bookRoom("Boardroom", "Priya", at(14, 0), at(15, 0), 8);
        System.out.println("  Boardroom 14:00-15:00 held by " + meeting.id());
        System.out.println("  free for 14:30-14:45? " + scheduler.availableRooms(at(14, 30), at(14, 45), 8)
                .stream().map(Room::id).toList());

        System.out.println("  cancel " + meeting.id() + " -> " + scheduler.cancel(meeting.id()));
        System.out.println("  free for 14:30-14:45? " + scheduler.availableRooms(at(14, 30), at(14, 45), 8)
                .stream().map(Room::id).toList());

        System.out.println("  cancel " + meeting.id() + " again -> " + scheduler.cancel(meeting.id())
                + "   (idempotent, not an exception - retries and double clicks are normal)");
        System.out.println("""

                  Cancellation is why Meeting carries an id and why the calendar keeps a
                  meetingId -> start index. Without it, cancelling means scanning the day to find
                  the booking you are holding a reference to.\
                """);
    }

    // ---------------------------------------------------------------- 6

    private static void earliestSlot() {
        section("6. 'When can I get 30 minutes?'");

        MeetingScheduler scheduler = office();
        scheduler.bookRoom("Huddle", "Priya", at(9, 0), at(10, 0), 2);
        scheduler.bookRoom("Huddle", "Rahul", at(10, 0), at(11, 15), 2);
        scheduler.bookRoom("Focus", "Meera", at(9, 0), at(10, 45), 2);
        scheduler.bookRoom("Boardroom", "Arjun", at(9, 0), at(16, 0), 8);
        scheduler.bookRoom("Training", "Sana", at(9, 0), at(12, 0), 20);

        Optional<MeetingScheduler.Slot> slot =
                scheduler.earliestSlot(NINE_AM, Duration.ofMinutes(30), 2);
        slot.ifPresent(s -> System.out.println("  earliest 30 min for 2 people: " + s.room().id()
                + "  " + HHMM.format(s.start()) + "-" + HHMM.format(s.end())));

        Optional<MeetingScheduler.Slot> bigSlot =
                scheduler.earliestSlot(NINE_AM, Duration.ofMinutes(30), 18);
        bigSlot.ifPresent(s -> System.out.println("  earliest 30 min for 18 people: " + s.room().id()
                + "  " + HHMM.format(s.start()) + "-" + HHMM.format(s.end())));

        System.out.println("""

                  The search jumps the cursor to the END of whatever is blocking it, so a fully
                  booked morning costs one step per meeting. Advancing by 15 minute increments
                  looks identical on this data and does 12 probes to reach 12:00 instead of 1.\
                """);
    }

    // ---------------------------------------------------------------- 7

    private static void conflictDetectionCost() {
        section("7. Two probes against a scan");

        int meetings = 50_000;
        int queries = 2_000;

        RoomCalendar calendar = new RoomCalendar("Boardroom");
        List<Meeting> asList = new ArrayList<>(meetings);
        for (int i = 0; i < meetings; i++) {
            // 30 minutes busy at the top of every hour, leaving a free half hour after it.
            Meeting meeting = new Meeting("mtg-" + i, "Boardroom", "load",
                    NINE_AM.plus(Duration.ofHours(i)),
                    NINE_AM.plus(Duration.ofHours(i)).plus(Duration.ofMinutes(30)), 2);
            calendar.add(meeting);
            asList.add(meeting);
        }

        List<Instant> probes = new ArrayList<>(queries);
        for (int i = 0; i < queries; i++) {
            // Land in the free half hour, so both approaches must do their full work.
            probes.add(NINE_AM.plus(Duration.ofHours((long) i * 7 % meetings)).plus(Duration.ofMinutes(35)));
        }

        boolean agreed = true;

        long treeNanos = 0;
        for (Instant probe : probes) {
            long t = System.nanoTime();
            boolean free = calendar.isFree(probe, probe.plus(Duration.ofMinutes(20)));
            treeNanos += System.nanoTime() - t;
            agreed &= free;
        }

        long scanNanos = 0;
        for (Instant probe : probes) {
            Instant end = probe.plus(Duration.ofMinutes(20));
            long t = System.nanoTime();
            boolean free = true;
            for (Meeting meeting : asList) {
                if (meeting.overlaps(probe, end)) {
                    free = false;
                    break;
                }
            }
            scanNanos += System.nanoTime() - t;
            agreed &= free;
        }

        System.out.printf("      %,d meetings in one room, %,d availability checks%n", meetings, queries);
        System.out.printf("      TreeMap floor+ceiling (O(log n))   %7.1f ms%n", treeNanos / 1_000_000.0);
        System.out.printf("      List scan             (O(n))       %7.1f ms%n", scanNanos / 1_000_000.0);
        System.out.println("      identical verdict on every check: " + agreed);

        System.out.println("""

                  The scan is not wrong, it is just linear on the path a booking UI hits on every
                  keystroke. The invariant 'meetings in this room never overlap' is what collapses
                  the search to two probes: only the meeting starting just before you and the one
                  starting just after you can possibly reach into your slot.\
                """);
    }

    // ---------------------------------------------------------------- 8

    private static void doubleBookingRace() throws Exception {
        section("8. The double booking race, reproduced");

        System.out.println("  20 threads, one room, the same 10:00-11:00 slot.");

        MeetingScheduler scheduler = office();
        int threads = 20;
        AtomicInteger booked = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch startLine = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.execute(() -> {
                    try {
                        startLine.await();
                        scheduler.bookRoom("Boardroom", "racer", at(10, 0), at(11, 0), 8);
                        booked.incrementAndGet();
                    } catch (BookingConflictException conflict) {
                        rejected.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        finished.countDown();
                    }
                });
            }
            startLine.countDown();
            finished.await(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        System.out.println("  booked:   " + booked.get());
        System.out.println("  rejected: " + rejected.get());
        System.out.println("  meetings actually on the Boardroom calendar: "
                + scheduler.scheduleFor("Boardroom").size());
        System.out.println("""

                  Exactly one winner, because the check and the insert happen inside one
                  synchronized block on that room's calendar. Split them and every one of those
                  20 threads sees a free slot before any of them writes.

                  Note the lock is per room, not global: a 21st thread booking Huddle at 10:00
                  never touches this lock. That parallelism is why roomByMeetingId has to be a
                  ConcurrentHashMap - per-room locks do not protect anything shared across rooms.\
                """);
    }

    // ---------------------------------------------------------------- 9

    private static void scopeNotes() {
        section("9. What you would cut, and what you would say instead");

        System.out.println("""
                  Cut, and say you are cutting them:
                    - recurring meetings: an expansion problem (RRULE, exceptions, 'this and all
                      future occurrences'). It is a whole interview by itself.
                    - timezones: store Instant, render in the viewer's zone. One sentence, no code.
                      Storing LocalDateTime plus a zone string is the bug that eats a sprint.
                    - attendee availability: intersecting k people's calendars is a merge over k
                      sorted interval lists. Name the algorithm, do not write it.
                    - persistence, notifications, permissions, waitlists.

                  Build, because they are what is graded:
                    - half-open overlap, stated as one predicate
                    - conflict detection that is not a scan
                    - check-and-insert under one lock
                    - cancellation by id

                  Follow-ups worth pre-empting out loud:
                    - 'What if two people book different rooms at once?'  -> per-room locks.
                    - 'What if this is three servers, not one?'           -> the lock moves to the
                      database: a unique constraint or SELECT ... FOR UPDATE on the room+slot.
                      In-process synchronized silently stops working the day you scale out.
                    - 'Find a slot for these 6 people'                    -> merge their busy
                      intervals, walk the gaps, intersect with room availability.\
                """);
    }

    // ---------------------------------------------------------------- helpers

    private static MeetingScheduler office() {
        MeetingScheduler scheduler = new MeetingScheduler();
        scheduler.addRoom(new Room("Huddle", 3, 1));
        scheduler.addRoom(new Room("Focus", 4, 2));
        scheduler.addRoom(new Room("Boardroom", 10, 3));
        scheduler.addRoom(new Room("Training", 24, 1));
        return scheduler;
    }

    private static Instant at(int hour, int minute) {
        return NINE_AM.plus(Duration.ofHours(hour - 9L)).plus(Duration.ofMinutes(minute));
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
