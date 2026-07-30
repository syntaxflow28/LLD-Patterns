package com.lld.problems.amazonlocker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable walk-through of the Amazon Locker design.
 *
 * <pre>
 *   javac -d out (Get-ChildItem -Recurse -Filter *.java src).FullName
 *   java -cp out com.lld.problems.amazonlocker.AmazonLockerDemo
 * </pre>
 *
 * <p><b>Is this LLD or HLD?</b> LLD. The deliverable is a class diagram and working code: entities,
 * a reservation lifecycle, two allocation axes, a secret with a TTL, and a concurrency protocol. It
 * turns into an HLD question only when the interviewer scales it — geospatial sharding, IoT
 * connectivity to the cabinets, notification fan-out. Design the classes first; that pivot is a
 * follow-up, not the question.
 *
 * <p>Patterns in play, and the requirement that forced each one:
 * <ul>
 *   <li><b>Strategy</b> (allocation) &larr; "put the parcel in the smallest door that fits"</li>
 *   <li><b>Strategy</b> (code policy) &larr; keypad PIN today, scannable code tomorrow</li>
 *   <li><b>Observer</b>              &larr; "text the customer their pickup code"</li>
 *   <li><b>Builder</b>               &larr; two windows, two strategies, a station layout</li>
 *   <li><b>Facade</b>                &larr; three actors want four methods, not twenty</li>
 *   <li><b>State</b> (as an enum)    &larr; the reservation lifecycle, sized honestly</li>
 * </ul>
 *
 * <p>The three things this drills that the parking lot does not: a <b>secret</b> that must be hashed
 * and rate-limited, <b>two independent expiry windows</b>, and a resource that is claimed by one
 * actor and released by a different one.
 */
public class AmazonLockerDemo {

    public static void main(String[] args) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-30T08:00:00Z"));

        LockerService network = new LockerService.Builder("Bengaluru Locker Network")
                .addStation("BLR-01", "Indiranagar", 12.9784, 77.6408,
                        Map.of(LockerSize.SMALL, 2, LockerSize.MEDIUM, 1,
                                LockerSize.LARGE, 1, LockerSize.EXTRA_LARGE, 1))
                .addStation("BLR-02", "Koramangala", 12.9352, 77.6245,
                        Map.of(LockerSize.SMALL, 1, LockerSize.MEDIUM, 1))
                .addStation("BLR-03", "Whitefield", 12.9698, 77.7500,
                        Map.of(LockerSize.SMALL, 2, LockerSize.LARGE, 1))
                .clock(clock)
                .dropOffWindow(Duration.ofHours(12))
                .retentionWindow(Duration.ofDays(3))
                .maxFailedAttempts(3)
                .allocationStrategy(new LockerAllocationStrategy.SmallestFit())
                .accessCodePolicy(new AccessCodePolicy.NumericPin(6))
                .build();

        network.addListener(new LockerEventListener.AuditLog());
        network.addListener(new LockerEventListener.CustomerNotifier());

        happyPath(network);
        smallestFitAndLockout(network);
        retentionWindowElapses(network, clock);
        courierNoShow(network, clock);
        nothingFitsHere(network);
        couriersRaceForDoors();
        twoPeopleOneCode();

        System.out.println("\nDone.");
    }

    /** Reserve near the customer, courier fills it, customer empties it. */
    private static void happyPath(LockerService network) {
        section("1. Book the nearest station, drop off, collect");

        double homeLat = 12.9750;
        double homeLon = 77.6400;
        System.out.println("  Stations by distance from home:");
        for (LockerLocation station : network.nearestStations(homeLat, homeLon, 3)) {
            System.out.printf("    %-28s %.2f km%n", station, station.distanceKmTo(homeLat, homeLon));
        }

        Parcel headphones = new Parcel("TRK-1001", "ORD-9001", LockerSize.MEDIUM, "+919845011111");
        LockerAssignment assignment = network.reserveNearest(headphones, homeLat, homeLon);
        System.out.println("  Courier told   : door " + assignment.lockerId()
                + " at " + assignment.locationId() + ", by " + assignment.dropOffDeadline());

        String code = network.dropOff(assignment.reservationId());
        Parcel collected = network.pickUp(assignment.lockerId(), code);
        System.out.println("  Collected      : " + collected);
    }

    /** Best-fit keeps the big doors for big parcels; then someone starts guessing at the keypad. */
    private static void smallestFitAndLockout(LockerService network) {
        section("2. SmallestFit protects the big doors");

        System.out.println("  BLR-01 free    : " + network.availability("BLR-01"));
        Parcel simCard = new Parcel("TRK-1002", "ORD-9002", LockerSize.SMALL, "+919845022222");
        LockerAssignment small = network.reserve(simCard, "BLR-01");
        System.out.println("  SMALL parcel   : door " + small.lockerId() + " (" + small.lockerSize()
                + ") - the EXTRA_LARGE door stays free for the stroller that arrives at noon");

        section("3. Three wrong codes lock the keypad, including for the right code");

        String code = network.dropOff(small.reservationId());
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                network.pickUp(small.lockerId(), wrong);
            } catch (LockerService.AccessDeniedException ex) {
                System.out.println("  Attempt " + attempt + "      : " + ex.reason() + " - " + ex.getMessage());
            }
        }

        try {
            network.pickUp(small.lockerId(), code);
        } catch (LockerService.AccessDeniedException ex) {
            System.out.println("  Correct code   : " + ex.reason() + " - " + ex.getMessage());
        }

        network.clearLockout(small.lockerId());
        System.out.println("  Support cleared the keypad after verifying identity");
        System.out.println("  Collected      : " + network.pickUp(small.lockerId(), code));
    }

    /** The parcel nobody picks up. Somebody has to go and get it. */
    private static void retentionWindowElapses(LockerService network, MutableClock clock) {
        section("4. Retention window elapses - the sweep returns the parcel to the carrier");

        Parcel book = new Parcel("TRK-1003", "ORD-9003", LockerSize.SMALL, "+919845033333");
        LockerAssignment assignment = network.reserve(book, "BLR-01");
        String code = network.dropOff(assignment.reservationId());

        clock.advance(Duration.ofDays(4));
        System.out.println("  Four days pass, then the sweep runs:");
        int reclaimed = network.reclaimExpired();
        System.out.println("  Reclaimed      : " + reclaimed + " door(s)");

        try {
            network.pickUp(assignment.lockerId(), code);
        } catch (LockerService.AccessDeniedException ex) {
            System.out.println("  Old SMS now    : " + ex.reason() + " - " + ex.getMessage());
        }
        System.out.println("  BLR-01 free    : " + network.availability("BLR-01"));
    }

    /** The other window: a door held for a van that never came. */
    private static void courierNoShow(LockerService network, MutableClock clock) {
        section("5. Courier no-show - the drop-off window frees the door too");

        Parcel monitor = new Parcel("TRK-1004", "ORD-9004", LockerSize.LARGE, "+919845044444");
        LockerAssignment assignment = network.reserve(monitor, "BLR-01");
        System.out.println("  Held door      : " + assignment.lockerId()
                + " until " + assignment.dropOffDeadline());

        clock.advance(Duration.ofHours(13));
        int reclaimed = network.reclaimExpired();
        System.out.println("  Sweep reclaimed: " + reclaimed + " door(s)");

        try {
            network.dropOff(assignment.reservationId());
        } catch (IllegalStateException ex) {
            System.out.println("  Late courier   : " + ex.getMessage());
        }
    }

    /** A station with no door big enough is not a failure — it is a routing decision. */
    private static void nothingFitsHere(LockerService network) {
        section("6. Nothing fits here - reroute instead of failing");

        Parcel stroller = new Parcel("TRK-1005", "ORD-9005", LockerSize.LARGE, "+919845055555");
        try {
            network.reserve(stroller, "BLR-02");
        } catch (LockerService.NoLockerAvailableException ex) {
            System.out.println("  Koramangala    : " + ex.getMessage());
        }

        LockerAssignment rerouted = network.reserveNearest(stroller, 12.9352, 77.6245);
        System.out.println("  Rerouted to    : door " + rerouted.lockerId()
                + " at " + rerouted.locationId());
    }

    /**
     * Proves the CAS claim protocol at allocation. 40 couriers, 5 SMALL doors — the counts must add
     * up exactly, with no door promised to two parcels.
     */
    private static void couriersRaceForDoors() throws InterruptedException {
        section("7. 40 couriers race for 5 doors");

        LockerService stress = new LockerService.Builder("Stress")
                .addStation("S-01", "Stress Station", 0, 0, Map.of(LockerSize.SMALL, 5))
                .build();

        int couriers = 40;
        AtomicInteger booked = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(16);

        for (int i = 0; i < couriers; i++) {
            int id = i;
            pool.submit(() -> {
                try {
                    start.await();
                    stress.reserve(
                            new Parcel("RACE-" + id, "ORD-" + id, LockerSize.SMALL, "+910000000000"),
                            "S-01");
                    booked.incrementAndGet();
                } catch (LockerService.NoLockerAvailableException ex) {
                    refused.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("  Booked         : " + booked.get() + " (expected 5)");
        System.out.println("  Refused        : " + refused.get() + " (expected 35)");
        System.out.println("  Doors free     : " + stress.availability("S-01").get(LockerSize.SMALL)
                + " (expected 0)");
    }

    /**
     * The race most designs miss. A family shares the pickup code and two people tap "open" at the
     * same instant: both pass the code check, because the code is correct for both. Only the CAS in
     * {@code Locker.tryRelease} decides who gets the parcel.
     */
    private static void twoPeopleOneCode() throws InterruptedException {
        section("8. Two people, one correct code - the parcel is handed over once");

        LockerService single = new LockerService.Builder("Single door")
                .addStation("S-02", "One Door", 0, 0, Map.of(LockerSize.MEDIUM, 1))
                .build();

        LockerAssignment assignment = single.reserve(
                new Parcel("TRK-2001", "ORD-2001", LockerSize.MEDIUM, "+910000000000"), "S-02");
        String code = single.dropOff(assignment.reservationId());

        AtomicInteger handedOver = new AtomicInteger();
        AtomicInteger denied = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    single.pickUp(assignment.lockerId(), code);
                    handedOver.incrementAndGet();
                } catch (LockerService.AccessDeniedException ex) {
                    denied.incrementAndGet();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("  Handed over    : " + handedOver.get() + " (expected 1)");
        System.out.println("  Denied         : " + denied.get() + " (expected 1)");
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    /** Test double for time. Real code gets {@code Clock.systemUTC()} injected instead. */
    static final class MutableClock extends Clock {

        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
