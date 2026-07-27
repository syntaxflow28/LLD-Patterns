package com.lld.problems.parkinglot;

/**
 * The kinds of vehicle the lot knows about.
 *
 * <p>Interview note: keep this an enum, not a class hierarchy. A {@code Motorcycle extends Vehicle}
 * hierarchy adds subclasses that carry no behaviour — the only thing that varies is "which spots
 * fit me" and "what do I pay", and both of those live in strategies, not in the vehicle.
 * Inheritance without differing behaviour is just ceremony.
 */
public enum VehicleType {
    MOTORCYCLE,
    CAR,
    ELECTRIC_CAR,
    TRUCK
}
