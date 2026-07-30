package com.lld.problems.amazonlocker;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One physical door. Also the entire concurrency story of this design.
 *
 * <p><b>The race the interviewer is looking for.</b> Two couriers scan parcels at the same station
 * in the same millisecond and the allocator picks door 14 for both. Written the obvious way —
 * {@code if (locker.isFree()) locker.assign(reservation);} — that is a check-then-act race: both
 * threads see "free", both write, and the second courier's parcel is destined for a door that is
 * about to be opened by someone else's customer.
 *
 * <p><b>The fix.</b> Every state change is a compare-and-set on an {@link AtomicReference} holding
 * an immutable {@link Reservation}. Exactly one thread wins each CAS; losers see {@code false} and
 * try the next door. No lock on the station, so a thousand doors are claimed in parallel — and
 * critically, this is the same protocol at drop-off and at pickup, not just at allocation. Two
 * people entering the correct code simultaneously both pass the code check; only one wins
 * {@link #tryRelease}, so the parcel is handed over exactly once.
 *
 * <p><b>Why the failed-attempt counter lives on the locker and not on the reservation.</b> Brute
 * force is a property of the keypad, not of the parcel: the attacker stands in front of door 14 and
 * types. Keeping the count here means it survives reservation objects being swapped, and resets when
 * the door is genuinely reassigned.
 */
public class Locker {

    private final String id;
    private final LockerSize size;

    /** null == free. Mutated only through CAS, never by a plain write. */
    private final AtomicReference<Reservation> reservation = new AtomicReference<>();

    private final AtomicInteger failedAttempts = new AtomicInteger();

    /** Doors break. A jammed door must stop being allocated without deleting it from the station. */
    private volatile boolean inService = true;

    public Locker(String id, LockerSize size) {
        this.id = id;
        this.size = size;
    }

    public String id() {
        return id;
    }

    public LockerSize size() {
        return size;
    }

    public boolean isFree() {
        return reservation.get() == null;
    }

    public boolean isInService() {
        return inService;
    }

    public void setInService(boolean inService) {
        this.inService = inService;
    }

    /** A hint only — by the time you act on it, the answer may be stale. The CAS is the truth. */
    public boolean canHold(LockerSize parcelSize) {
        return inService && isFree() && size.accommodates(parcelSize);
    }

    // ---------------------------------------------------------------- state machine (CAS-only)

    /**
     * Atomically claims this door. Package-private: only an allocation strategy may call it, so no
     * caller can bypass the claim protocol.
     *
     * @return true if this thread claimed the door, false if someone else got there first
     */
    boolean tryReserve(Reservation newReservation) {
        if (!inService) {
            return false;
        }
        boolean claimed = reservation.compareAndSet(null, newReservation);
        if (claimed) {
            failedAttempts.set(0);
        }
        return claimed;
    }

    /** Swaps one reservation for its successor. Fails if another thread already moved the door on. */
    boolean tryTransition(Reservation expected, Reservation updated) {
        return reservation.compareAndSet(expected, updated);
    }

    /** Frees the door. Exactly one caller can win, which is what makes double-pickup impossible. */
    boolean tryRelease(Reservation expected) {
        boolean released = reservation.compareAndSet(expected, null);
        if (released) {
            failedAttempts.set(0);
        }
        return released;
    }

    Reservation currentReservation() {
        return reservation.get();
    }

    int recordFailedAttempt() {
        return failedAttempts.incrementAndGet();
    }

    boolean isLockedOut(int maxAttempts) {
        return failedAttempts.get() >= maxAttempts;
    }

    int failedAttempts() {
        return failedAttempts.get();
    }

    void clearLockout() {
        failedAttempts.set(0);
    }

    @Override
    public String toString() {
        return id + " [" + size + "]";
    }
}
