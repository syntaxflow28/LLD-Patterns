package com.lld.problems.amazonlocker;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A station: the cabinet in a supermarket lobby, and the doors in it.
 *
 * <p><b>Why locations are first-class.</b> "Find me a locker near my office" is the requirement that
 * separates this problem from the parking lot. A single flat pool of doors cannot answer it, so
 * geography belongs in the model — and it is a second, independent axis of choice: <em>which
 * station</em> (geography, opening hours) and then <em>which door</em>
 * ({@link LockerAllocationStrategy}).
 *
 * <p><b>Where this stops scaling, and what to say.</b> Ranking every station by distance is O(n) per
 * lookup — fine for the thousands of stations one city has, wrong for a country. The follow-up
 * answer is a geospatial index: geohash or S2 cell prefixes, query the customer's cell plus its
 * neighbours, rank only those. Name it and move on; do not implement it in a 45-minute round.
 */
public final class LockerLocation {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private final String id;
    private final String name;
    private final double latitude;
    private final double longitude;
    private final List<Locker> lockers;

    public LockerLocation(String id, String name, double latitude, double longitude, List<Locker> lockers) {
        if (lockers.isEmpty()) {
            throw new IllegalArgumentException("A station needs at least one locker: " + id);
        }
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.lockers = List.copyOf(lockers);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Unmodifiable: doors are installed by an engineer, not added at runtime by a caller. */
    public List<Locker> lockers() {
        return lockers;
    }

    /** Haversine. Straight-line, not walking distance — good enough to rank nearby stations. */
    public double distanceKmTo(double lat, double lon) {
        double dLat = Math.toRadians(lat - latitude);
        double dLon = Math.toRadians(lon - longitude);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(lat))
                * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public Map<LockerSize, Long> availability() {
        Map<LockerSize, Long> free = new EnumMap<>(LockerSize.class);
        for (LockerSize size : LockerSize.values()) {
            free.put(size, lockers.stream()
                    .filter(locker -> locker.size() == size && locker.isFree() && locker.isInService())
                    .count());
        }
        return free;
    }

    /** Can this station take the parcel at all, right now? Used to skip stations before ranking. */
    public boolean hasSpaceFor(LockerSize parcelSize) {
        return lockers.stream().anyMatch(locker -> locker.canHold(parcelSize));
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
