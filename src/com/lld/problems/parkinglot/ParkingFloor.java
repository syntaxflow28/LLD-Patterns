package com.lld.problems.parkinglot;

import java.util.List;
import java.util.Objects;

/**
 * A floor owns its spots (composition — destroy the floor and the spots go with it).
 *
 * <p>The spot list is unmodifiable so nobody can add spots at runtime behind the lot's back;
 * mutation happens <em>inside</em> each {@link ParkingSpot}, not to the collection.
 */
public class ParkingFloor {

    private final int number;
    private final List<ParkingSpot> spots;

    public ParkingFloor(int number, List<ParkingSpot> spots) {
        this.number = number;
        this.spots = List.copyOf(Objects.requireNonNull(spots, "spots"));
    }

    public int number() {
        return number;
    }

    public List<ParkingSpot> spots() {
        return spots;
    }

    public long freeCount(SpotType type) {
        return spots.stream().filter(s -> s.type() == type && s.isFree()).count();
    }

    @Override
    public String toString() {
        return "Floor " + number;
    }
}
