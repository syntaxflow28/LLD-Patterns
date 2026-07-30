package com.lld.problems.amazonlocker;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * STRATEGY — given a station and a parcel, which door?
 *
 * <p>The named axis of change. Operations will want different answers at different sites: fill in
 * order for a wall a courier walks along, best-fit for a busy station, keep the bottom row free for
 * wheelchair users, never put a cold-chain parcel in a door with no refrigeration.
 *
 * <p><b>The contract every implementation must honour:</b> claim the door with
 * {@link Locker#tryReserve} and treat {@code false} as "someone beat me, keep looking". Anything
 * that returns a door it has not CAS-claimed reintroduces the double-allocation race.
 */
public interface LockerAllocationStrategy {

    /**
     * @return the claimed door, or empty if nothing at this station fits the parcel right now
     */
    Optional<Locker> allocate(LockerLocation location, Reservation reservation);

    /** First fitting door in installation order. O(doors), no sorting, no allocation. */
    final class FirstFit implements LockerAllocationStrategy {

        @Override
        public Optional<Locker> allocate(LockerLocation location, Reservation reservation) {
            LockerSize required = reservation.parcel().size();
            for (Locker locker : location.lockers()) {
                if (locker.canHold(required) && locker.tryReserve(reservation)) {
                    return Optional.of(locker);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Smallest door the parcel fits in.
     *
     * <p><b>Why it is worth the sort.</b> With {@code FirstFit}, a phone case can land in the one
     * EXTRA_LARGE door, and the stroller that arrives an hour later is refused while three SMALL
     * doors sit empty. Small doors outnumber large ones roughly ten to one in a real cabinet, so
     * wasting a large door is expensive. This is the direct answer to <em>"how do you stop small
     * parcels from starving big ones?"</em>
     */
    final class SmallestFit implements LockerAllocationStrategy {

        @Override
        public Optional<Locker> allocate(LockerLocation location, Reservation reservation) {
            LockerSize required = reservation.parcel().size();
            List<Locker> candidates = location.lockers().stream()
                    .filter(locker -> locker.canHold(required))
                    .sorted(Comparator.comparingInt(locker -> locker.size().capacity()))
                    .toList();

            // canHold() above was only a hint; tryReserve() is the real claim.
            for (Locker locker : candidates) {
                if (locker.tryReserve(reservation)) {
                    return Optional.of(locker);
                }
            }
            return Optional.empty();
        }
    }
}
