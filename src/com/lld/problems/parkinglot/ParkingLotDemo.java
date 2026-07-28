package com.lld.problems.parkinglot;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable walk-through of the Parking Lot design.
 *
 * <pre>
 *   javac -d out (Get-ChildItem -Recurse -Filter *.java src).FullName
 *   java -cp out com.lld.problems.parkinglot.ParkingLotDemo
 * </pre>
 *
 * <p>Patterns in play, and the requirement that forced each one:
 * <ul>
 *   <li><b>Strategy</b> (fees)      &larr; "pricing depends on vehicle type and duration"</li>
 *   <li><b>Strategy</b> (allocation)&larr; "assign the nearest available spot"</li>
 *   <li><b>Decorator</b> (discount) &larr; "we run promotions"</li>
 *   <li><b>Observer</b>             &larr; "display board shows availability"</li>
 *   <li><b>Builder</b>              &larr; multi-floor layout with optional knobs</li>
 *   <li><b>Facade</b>               &larr; entry/exit terminals want two methods, not twelve</li>
 * </ul>
 */
public class ParkingLotDemo {

    public static void main(String[] args) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T09:00:00Z"));

        FeeStrategy weekday = new FeeStrategy.HourlyTiered(
                new BigDecimal("40.00"),
                Map.of(
                        VehicleType.MOTORCYCLE, new BigDecimal("10.00"),
                        VehicleType.CAR, new BigDecimal("30.00"),
                        VehicleType.ELECTRIC_CAR, new BigDecimal("25.00"),
                        VehicleType.TRUCK, new BigDecimal("60.00")));

        ParkingLot lot = new ParkingLot.Builder("Phoenix Mall")
                .addFloor(0, Map.of(SpotType.MOTORCYCLE, 2, SpotType.COMPACT, 2, SpotType.LARGE, 1))
                .addFloor(1, Map.of(SpotType.COMPACT, 3, SpotType.ELECTRIC, 1))
                .clock(clock)
                .feeStrategy(weekday)
                .allocationStrategy(new SpotAllocationStrategy.BestFit())
                .build();

        lot.addListener(new ParkingLotListener.AuditLog());
        lot.addListener(new ParkingLotListener.DisplayBoard(lot));

        section("1. Park a few vehicles (BestFit: smallest spot that works)");
        Ticket bike = lot.park(new Vehicle("KA-01-B-1111", VehicleType.MOTORCYCLE));
        Ticket car = lot.park(new Vehicle("KA-01-C-2222", VehicleType.CAR));
        Ticket ev = lot.park(new Vehicle("KA-01-E-3333", VehicleType.ELECTRIC_CAR));

        section("2. Exit after 2h15m - part hours are billed as full hours");
        clock.advance(Duration.ofMinutes(135));
        System.out.println("  Motorcycle fee : " + lot.unpark(bike.id()));
        System.out.println("  Car fee        : " + lot.unpark(car.id()));

        section("3. Swap the tariff at runtime - nothing else changes");
        lot.setFeeStrategy(new FeeStrategy.PercentDiscount(weekday, new BigDecimal("20")));
        System.out.println("  Active tariff  : " + "Hourly - 20% off");
        clock.advance(Duration.ofMinutes(45));
        System.out.println("  EV fee         : " + lot.unpark(ev.id()));

        section("4. Truck can only use LARGE; a second truck is refused");
        Ticket truck = lot.park(new Vehicle("KA-01-T-4444", VehicleType.TRUCK));
        System.out.println("  Truck parked at: " + truck.spot());
        try {
            lot.park(new Vehicle("KA-01-T-5555", VehicleType.TRUCK));
        } catch (ParkingLot.NoSpotAvailableException ex) {
            System.out.println("  Rejected       : " + ex.getMessage());
        }

        section("5. Double exit is rejected - remove() wins exactly once");
        lot.unpark(truck.id());
        try {
            lot.unpark(truck.id());
        } catch (IllegalArgumentException ex) {
            System.out.println("  Rejected       : " + ex.getMessage());
        }

        section("6. Concurrency: 50 cars race for 5 compact/large spots");
        raceForSpots();

        System.out.println("\nDone.");
    }

    /**
     * Proves the CAS claim protocol. 50 threads, 5 usable spots for cars (2 compact on floor 0,
     * 3 compact on floor 1, 1 large, minus none taken) — the counts must add up exactly, with no
     * spot handed to two cars.
     */
    private static void raceForSpots() throws InterruptedException {
        ParkingLot lot = new ParkingLot.Builder("Stress")
                .addFloor(0, Map.of(SpotType.COMPACT, 5))
                .feeStrategy(new FeeStrategy.DayPass(new BigDecimal("500.00")))
                .build();

        int drivers = 50;
        AtomicInteger parked = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);

        for (int i = 0; i < drivers; i++) {
            int id = i;
            pool.submit(() -> {
                try {
                    lot.park(new Vehicle("RACE-" + id, VehicleType.CAR));
                    parked.incrementAndGet();
                } catch (ParkingLot.NoSpotAvailableException ex) {
                    refused.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("  Parked         : " + parked.get() + " (expected 5)");
        System.out.println("  Refused        : " + refused.get() + " (expected 45)");
        System.out.println("  Spots free     : " + lot.availability().get(SpotType.COMPACT) + " (expected 0)");
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    /** Test double for time. Real code gets {@code Clock.systemUTC()} injected instead. */
    static final class MutableClock extends Clock {

        private Instant now;

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
