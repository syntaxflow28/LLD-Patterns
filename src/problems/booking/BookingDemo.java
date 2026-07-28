package problems.booking;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable walk-through of the movie ticket booking design.
 *
 * <pre>
 *   java -cp out problems.booking.BookingDemo
 * </pre>
 *
 * <p>Sections 3 to 6 are the interview. Everything before them is setup, and everything after is
 * garnish. If you only rehearse one part of this problem, rehearse explaining why the seat hold has
 * a TTL and why payment re-validates it.
 */
public class BookingDemo {

    private static final String SHOW_ID = "SHOW-9PM";

    public static void main(String[] args) throws Exception {

        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T18:00:00Z"));

        section("1. The show and its seat map");
        Show show = new Show(SHOW_ID, "Dune: Part Three", "Screen 1",
                Instant.parse("2026-07-27T21:00:00Z"), new BigDecimal("200.00"), Show.layout(3, 5));
        BookingService service = newService(clock, new PricingStrategy.BySeatType());
        service.addShow(show);
        System.out.println("  " + show);
        printSeatMap(service);

        section("2. Happy path: hold -> quote -> pay -> confirmed");
        Booking booking = service.selectSeats(SHOW_ID, "priya", List.of("A1", "A2"));
        System.out.println("  held      " + booking);
        System.out.println("  pay by    " + booking.expiresAt() + " (a 5 minute hold)");
        System.out.println("  A1 and A2 are now hidden from other users while priya pays:");
        printSeatMap(service);
        service.confirmPayment(booking.id());
        System.out.println("  confirmed " + booking);

        section("3. THE RACE: 20 threads go for the same seat at the same instant");
        MutableClock raceClock = new MutableClock(Instant.parse("2026-07-27T18:00:00Z"));
        BookingService raceService = newService(raceClock, new PricingStrategy.BySeatType());
        Show raceShow = new Show("SHOW-RACE", "Dune: Part Three", "Screen 2",
                Instant.parse("2026-07-27T21:00:00Z"), new BigDecimal("200.00"), Show.layout(3, 5));
        raceService.addShow(raceShow);

        AtomicInteger won = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        CountDownLatch startGun = new CountDownLatch(1); // maximise the overlap
        ExecutorService pool = Executors.newFixedThreadPool(20);

        for (int i = 0; i < 20; i++) {
            String user = "user-" + i;
            pool.submit(() -> {
                try {
                    startGun.await();
                    Booking attempt = raceService.selectSeats("SHOW-RACE", user, List.of("B3"));
                    raceService.confirmPayment(attempt.id());
                    won.incrementAndGet();
                } catch (SeatLockProvider.SeatUnavailableException | IllegalStateException expected) {
                    refused.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        startGun.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("  seat B3 booked by : " + won.get() + " user(s)   (expected exactly 1)");
        System.out.println("  cleanly refused   : " + refused.get() + " user(s)");
        System.out.println("  Nobody got a double booking and nobody got an exception we did not plan for.");

        section("4. All-or-nothing: a partial hold is never left behind");
        service.selectSeats(SHOW_ID, "rahul", List.of("B1"));
        System.out.println("  rahul holds B1. Now sam asks for B1, B2, B3 together:");
        try {
            service.selectSeats(SHOW_ID, "sam", List.of("B1", "B2", "B3"));
            System.out.println("  UNEXPECTED: sam succeeded");
        } catch (SeatLockProvider.SeatUnavailableException expected) {
            System.out.println("      refused - " + expected.getMessage());
        }
        System.out.println("  B2 still free? " + isAvailable(service, "B2"));
        System.out.println("  B3 still free? " + isAvailable(service, "B3"));
        System.out.println("  Sam's failed attempt did NOT strand holds on B2 and B3. Without the");
        System.out.println("  check-all-then-take-all split, a few failed attempts would make the");
        System.out.println("  auditorium unbookable for everyone.");

        section("5. Holds expire, so an abandoned checkout cannot strand a seat forever");
        Booking abandoned = service.selectSeats(SHOW_ID, "vikram", List.of("C1"));
        System.out.println("  18:00  vikram holds C1, then closes the tab");
        System.out.println("  18:00  C1 available to others? " + isAvailable(service, "C1"));
        clock.advance(Duration.ofMinutes(6));
        System.out.println("  18:06  C1 available to others? " + isAvailable(service, "C1") + "  (hold lapsed)");

        Booking meera = service.selectSeats(SHOW_ID, "meera", List.of("C1"));
        service.confirmPayment(meera.id());
        System.out.println("  18:06  meera books C1 -> " + meera.status());

        section("6. A payment that arrives after the hold lapsed must NOT confirm");
        try {
            service.confirmPayment(abandoned.id());
            System.out.println("  UNEXPECTED: the stale booking confirmed");
        } catch (IllegalStateException expected) {
            System.out.println("      rejected - " + expected.getMessage());
        }
        System.out.println("  vikram's booking is now " + abandoned.status() + ".");
        System.out.println("  This re-check is the single most commonly missed step in this problem:");
        System.out.println("  the hold was taken correctly and then trusted forever.");

        section("7. A sold seat stays sold, hold or no hold");
        try {
            service.selectSeats(SHOW_ID, "arjun", List.of("A1"));
            System.out.println("  UNEXPECTED: A1 was re-sold");
        } catch (SeatLockProvider.SeatUnavailableException expected) {
            System.out.println("      refused - " + expected.getMessage());
        }
        System.out.println("  Holds are temporary; the sold set is permanent. Two mechanisms, two lifetimes.");

        section("8. Pricing strategies compose");
        Show pricingShow = new Show("SHOW-PRICE", "Dune: Part Three", "Screen 3",
                Instant.parse("2026-07-27T21:00:00Z"), new BigDecimal("200.00"), Show.layout(3, 5));
        List<PricingStrategy> strategies = List.of(
                new PricingStrategy.BySeatType(),
                new PricingStrategy.Surge(new PricingStrategy.BySeatType(), new BigDecimal("1.25")),
                new PricingStrategy.PercentOff(
                        new PricingStrategy.Surge(new PricingStrategy.BySeatType(), new BigDecimal("1.25")), 10));

        System.out.printf("  %-28s %10s %10s %10s%n", "strategy", "PLATINUM", "GOLD", "SILVER");
        for (PricingStrategy strategy : strategies) {
            System.out.printf("  %-28s %10s %10s %10s%n", strategy.name(),
                    strategy.priceFor(pricingShow, pricingShow.seat("A1").orElseThrow()),
                    strategy.priceFor(pricingShow, pricingShow.seat("B1").orElseThrow()),
                    strategy.priceFor(pricingShow, pricingShow.seat("C1").orElseThrow()));
        }
        System.out.println("  Surge then discount. Reverse the nesting and you get a different number -");
        System.out.println("  which is a business decision, not an implementation detail.");

        section("9. Cancellation returns the seat to the pool");
        System.out.println("  before cancel, C1 free? " + isAvailable(service, "C1"));
        service.cancel(meera.id());
        System.out.println("  after  cancel, C1 free? " + isAvailable(service, "C1")
                + "   booking is " + meera.status());
        System.out.println("  CANCELLED is deliberately not EXPIRED: money moved, so a refund is owed.");

        System.out.println("\nDone.");
    }

    private static BookingService newService(Clock clock, PricingStrategy pricing) {
        return new BookingService(
                new InMemorySeatLockProvider(Duration.ofMinutes(5), clock),
                pricing, Duration.ofMinutes(5), clock);
    }

    private static boolean isAvailable(BookingService service, String seatId) {
        return service.availableSeats(SHOW_ID).stream().anyMatch(seat -> seat.id().equals(seatId));
    }

    private static void printSeatMap(BookingService service) {
        Show show = service.show(SHOW_ID);
        List<Seat> free = service.availableSeats(SHOW_ID);
        StringBuilder map = new StringBuilder("      ");
        int lastRow = 0;
        for (Seat seat : show.allSeats()) {
            if (lastRow != 0 && seat.row() != lastRow) {
                map.append("\n      ");
            }
            lastRow = seat.row();
            map.append(free.contains(seat) ? seat.id() : "..").append(' ');
        }
        System.out.println(map);
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    /** Time under test control, so a 5-minute hold can be expired in a microsecond. */
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
