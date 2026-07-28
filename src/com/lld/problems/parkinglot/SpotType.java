package com.lld.problems.parkinglot;

import java.util.EnumSet;
import java.util.Set;

/**
 * The kinds of parking spot, and — crucially — which vehicles each one accepts.
 *
 * <p>Putting {@link #accommodates(VehicleType)} here rather than in a big {@code if/else} inside the
 * allocator is the difference between an anemic enum and a useful one. Adding a new vehicle type
 * later means editing this table and nothing else.
 *
 * <p>{@link #size()} exists so a "best fit" allocator can prefer the <em>smallest</em> spot that
 * works, keeping LARGE spots free for trucks that genuinely need them.
 */
public enum SpotType {

    MOTORCYCLE(1, EnumSet.of(VehicleType.MOTORCYCLE)),

    COMPACT(2, EnumSet.of(VehicleType.MOTORCYCLE, VehicleType.CAR, VehicleType.ELECTRIC_CAR)),

    /** Has a charger. Deliberately refuses petrol cars so chargers are not blocked. */
    ELECTRIC(2, EnumSet.of(VehicleType.ELECTRIC_CAR)),

    LARGE(3, EnumSet.allOf(VehicleType.class));

    private final int size;
    private final Set<VehicleType> accepts;

    SpotType(int size, Set<VehicleType> accepts) {
        this.size = size;
        this.accepts = accepts;
    }

    public boolean accommodates(VehicleType type) {
        return accepts.contains(type);
    }

    public int size() {
        return size;
    }
}
