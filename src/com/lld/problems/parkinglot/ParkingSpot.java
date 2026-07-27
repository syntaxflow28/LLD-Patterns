package com.lld.problems.parkinglot;

import java.util.concurrent.atomic.AtomicReference;

/**
 * A single physical spot.
 *
 * <p><b>The concurrency answer interviewers are fishing for.</b> Two cars arrive at the same
 * millisecond and the allocator picks the same free spot for both. A naive
 * {@code if (spot.isFree()) spot.occupy(v);} is a check-then-act race: both threads see "free",
 * both write, one car is parked on top of another.
 *
 * <p>The fix here is a compare-and-set on an {@link AtomicReference}. Exactly one thread wins the
 * CAS; the loser sees {@code false} and moves on to the next spot. No global lock on the lot, so
 * 10,000 spots can be claimed in parallel. The alternative — {@code synchronized} on the whole
 * {@code ParkingLot} — is correct but serialises every entry in the building.
 */
public class ParkingSpot {

    private final String id;
    private final int floor;
    private final SpotType type;

    /** null == free. Mutated only through CAS. */
    private final AtomicReference<Vehicle> occupant = new AtomicReference<>();

    public ParkingSpot(String id, int floor, SpotType type) {
        this.id = id;
        this.floor = floor;
        this.type = type;
    }

    public String id() {
        return id;
    }

    public int floor() {
        return floor;
    }

    public SpotType type() {
        return type;
    }

    public boolean isFree() {
        return occupant.get() == null;
    }

    public boolean canFit(VehicleType vehicleType) {
        return type.accommodates(vehicleType);
    }

    /**
     * Atomically claims this spot. Package-private: only an allocation strategy may call it, so
     * callers cannot bypass the claim protocol.
     *
     * @return true if this thread claimed the spot, false if someone else got there first
     */
    boolean tryOccupy(Vehicle vehicle) {
        return occupant.compareAndSet(null, vehicle);
    }

    void release() {
        occupant.set(null);
    }

    @Override
    public String toString() {
        return id + " [" + type + "]";
    }
}
