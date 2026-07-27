package com.lld.problems.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single-JVM implementation of the seat hold.
 *
 * <p><b>Why one {@code synchronized} block and not a {@code ConcurrentHashMap} per seat.</b> The
 * operation that must be atomic is not "put one lock" — it is <em>"check all N seats, then take all
 * N or none"</em>. A concurrent map makes each individual put atomic and still allows two threads to
 * both pass the check phase and then both start taking seats, ending up with two half-held sets and
 * two disappointed users. This is the same lesson as the LRU cache: <b>the compound operation is
 * what needs the lock, not the container.</b> Being able to explain <em>why</em> the obvious
 * concurrent collection is not sufficient is worth more here than using one.
 *
 * <p><b>Could you lock per seat instead?</b> Yes, and it scales better — but then you must acquire
 * several seat locks at once, and two threads grabbing {@code A1, A2} and {@code A2, A1} deadlock.
 * The fix is a total ordering: always acquire in sorted seat-id order. Mention that ordering rule
 * and you have pre-empted the deadlock follow-up. A single lock is chosen here because holds are
 * microseconds long and contention is per show, not global.
 *
 * <p><b>Why expiry is evaluated lazily on read.</b> No sweeper thread, no timer per lock. An expired
 * lock is simply one whose deadline has passed, and it is dropped the next time anyone looks at that
 * seat. Locks nobody asks about cost nothing. (A background sweep is still worth adding eventually
 * — otherwise abandoned entries for shows that already ended sit in the map forever.)
 *
 * <p><b>Why the {@link Clock} is injected.</b> So the demo can prove a five-minute hold expires
 * without taking five minutes. TTL behaviour that can only be tested by sleeping does not get
 * tested.
 */
public class InMemorySeatLockProvider implements SeatLockProvider {

    /** A hold. Immutable: expiry is a deadline, never a countdown that ticks. */
    private record SeatLock(String seatId, String userId, Instant expiresAt) {

        boolean isLiveAt(Instant now) {
            return now.isBefore(expiresAt);
        }
    }

    private final Duration lockDuration;
    private final Clock clock;

    /** Keyed by "showId:seatId" - locks are per screening, not per physical seat. */
    private final Map<String, SeatLock> locks = new HashMap<>();

    public InMemorySeatLockProvider(Duration lockDuration, Clock clock) {
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    @Override
    public synchronized void lockSeats(String showId, List<String> seatIds, String userId) {
        Instant now = clock.instant();

        // Phase 1: check every seat BEFORE taking any. This is what makes the operation
        // all-or-nothing, and it only works because the whole method is one critical section.
        List<String> unavailable = new ArrayList<>();
        for (String seatId : seatIds) {
            SeatLock existing = liveLock(showId, seatId, now);
            if (existing != null && !existing.userId().equals(userId)) {
                unavailable.add(seatId);
            }
        }
        if (!unavailable.isEmpty()) {
            throw new SeatUnavailableException("seats already held: " + unavailable, unavailable);
        }

        // Phase 2: take them all. Nothing can interleave between the phases.
        Instant expiresAt = now.plus(lockDuration);
        for (String seatId : seatIds) {
            // Re-locking your own seat refreshes the deadline, which is what you want when a user
            // edits their selection mid-checkout rather than starting over.
            locks.put(key(showId, seatId), new SeatLock(seatId, userId, expiresAt));
        }
    }

    @Override
    public synchronized void unlockSeats(String showId, List<String> seatIds, String userId) {
        for (String seatId : seatIds) {
            SeatLock existing = locks.get(key(showId, seatId));
            // Ownership check: without it, user B could release user A's hold and steal the seat.
            // Every lease-based lock needs this, and it is the bug people most often leave in.
            if (existing != null && existing.userId().equals(userId)) {
                locks.remove(key(showId, seatId));
            }
        }
    }

    @Override
    public synchronized boolean isLocked(String showId, String seatId) {
        return liveLock(showId, seatId, clock.instant()) != null;
    }

    @Override
    public synchronized Optional<String> lockedBy(String showId, String seatId) {
        SeatLock lock = liveLock(showId, seatId, clock.instant());
        return Optional.ofNullable(lock).map(SeatLock::userId);
    }

    @Override
    public synchronized boolean holdsAll(String showId, List<String> seatIds, String userId) {
        Instant now = clock.instant();
        for (String seatId : seatIds) {
            SeatLock lock = liveLock(showId, seatId, now);
            if (lock == null || !lock.userId().equals(userId)) {
                return false;
            }
        }
        return true;
    }

    /** Returns the lock if it exists and has not expired; evicts it and returns null if it has. */
    private SeatLock liveLock(String showId, String seatId, Instant now) {
        String key = key(showId, seatId);
        SeatLock lock = locks.get(key);
        if (lock == null) {
            return null;
        }
        if (!lock.isLiveAt(now)) {
            locks.remove(key); // lazy expiry, on read
            return null;
        }
        return lock;
    }

    private static String key(String showId, String seatId) {
        return showId + ":" + seatId;
    }
}
