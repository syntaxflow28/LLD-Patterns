package com.lld.problems.booking;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * FACADE — the booking flow, in the order it actually happens.
 *
 * <pre>
 *   selectSeats(...)   hold the seats, quote a price, start the clock
 *   confirmPayment(..) verify the hold is still ours, then sell
 * </pre>
 *
 * <p><b>The one thing that must be right in this whole problem</b> is the check in
 * {@link #confirmPayment}: a payment that arrives after the hold expired must not confirm, because
 * by then someone else may legitimately hold those seats. Skipping that re-validation is the most
 * common bug in candidate solutions — the hold gets taken correctly and then trusted forever.
 *
 * <p><b>Why seats are marked booked only at payment.</b> Before that they are held, not sold. The
 * hold is what stops a second user reaching checkout; the sale is what stops them forever. Two
 * different mechanisms for two different durations.
 *
 * <p><b>Why the lock provider is an interface here.</b> Everything above is unchanged when the
 * single-JVM implementation is swapped for Redis. That substitution is the entire point of the
 * abstraction, and it is the natural answer to "now run this on fifty servers".
 */
public class BookingService {

    private final SeatLockProvider lockProvider;
    private final PricingStrategy pricingStrategy;
    private final Clock clock;
    private final java.time.Duration holdDuration;

    private final Map<String, Show> shows = new ConcurrentHashMap<>();
    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();
    private final AtomicLong bookingIds = new AtomicLong(1000);

    public BookingService(SeatLockProvider lockProvider, PricingStrategy pricingStrategy,
                          java.time.Duration holdDuration, Clock clock) {
        this.lockProvider = lockProvider;
        this.pricingStrategy = pricingStrategy;
        this.holdDuration = holdDuration;
        this.clock = clock;
    }

    public void addShow(Show show) {
        shows.put(show.id(), show);
    }

    public Show show(String showId) {
        Show show = shows.get(showId);
        if (show == null) {
            throw new IllegalArgumentException("unknown show " + showId);
        }
        return show;
    }

    /**
     * Step 1: hold the seats and quote a price.
     *
     * <p>Not synchronized. The atomicity that matters is inside {@link SeatLockProvider#lockSeats},
     * and putting a second lock around it here would serialise every booking in the entire cinema
     * chain behind one monitor for no extra safety. Locking at the narrowest layer that owns the
     * invariant is a deliberate choice worth stating.
     */
    public Booking selectSeats(String showId, String userId, List<String> seatIds) {
        Show show = show(showId);

        // Validate before locking - a typo'd seat id should not take holds on the valid ones.
        for (String seatId : seatIds) {
            if (show.seat(seatId).isEmpty()) {
                throw new IllegalArgumentException("no such seat " + seatId + " in show " + showId);
            }
            if (show.isBooked(seatId)) {
                throw new SeatLockProvider.SeatUnavailableException(
                        "seat " + seatId + " is already sold", List.of(seatId));
            }
        }

        lockProvider.lockSeats(showId, seatIds, userId); // throws if anyone else holds one

        // Re-check the sold set AFTER taking the hold, and this is not paranoia - it closes a real
        // check-then-act window. The sequence that breaks without it:
        //
        //   t1  user A holds B3, pays, B3 is marked sold, A's hold is released
        //   t2  user B checked isBooked(B3) at t0 (false) and only now reaches lockSeats
        //   t3  B3 is unheld, so B's hold succeeds - on a seat that is already sold
        //
        // The hold makes concurrent HOLDS exclusive; it says nothing about a sale that completed
        // between our check and our hold. Re-reading the authoritative state once the hold is in
        // hand is the fix, and it is the same "validate under the lock you just took" discipline as
        // double-checked locking.
        List<String> alreadySold = seatIds.stream().filter(show::isBooked).toList();
        if (!alreadySold.isEmpty()) {
            lockProvider.unlockSeats(showId, seatIds, userId); // never leave a hold behind on failure
            throw new SeatLockProvider.SeatUnavailableException(
                    "seats sold while acquiring the hold: " + alreadySold, alreadySold);
        }

        BigDecimal total = seatIds.stream()
                .map(id -> pricingStrategy.priceFor(show, show.seat(id).orElseThrow()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant now = clock.instant();
        Booking booking = new Booking("BK-" + bookingIds.incrementAndGet(), showId, userId,
                seatIds, total, now, now.plus(holdDuration));
        bookings.put(booking.id(), booking);
        return booking;
    }

    /**
     * Step 2: money has cleared, so sell the seats.
     *
     * <p>Synchronized because it reads the hold, mutates the show's sold set and mutates the booking
     * status — three steps that must not interleave with another confirmation for the same seats.
     */
    public synchronized Booking confirmPayment(String bookingId) {
        Booking booking = booking(bookingId).orElseThrow(
                () -> new IllegalArgumentException("unknown booking " + bookingId));

        if (booking.status() != BookingStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("booking " + bookingId + " is " + booking.status()
                    + ", only PENDING_PAYMENT can be confirmed");
        }

        // THE CHECK THAT MATTERS. The hold may have expired while the user was on the payment page,
        // and another user may already hold - or have bought - these seats.
        if (!lockProvider.holdsAll(booking.showId(), booking.seatIds(), booking.userId())) {
            booking.transitionTo(BookingStatus.EXPIRED);
            throw new IllegalStateException("hold expired for booking " + bookingId
                    + "; seats were released and the payment must be refunded");
        }

        Show show = show(booking.showId());

        // Defence in depth: never sell a seat twice, whatever the hold says. Cheap, and it means a
        // future bug in the locking layer degrades into a clean rejection instead of a double sale.
        List<String> alreadySold = booking.seatIds().stream().filter(show::isBooked).toList();
        if (!alreadySold.isEmpty()) {
            booking.transitionTo(BookingStatus.EXPIRED);
            throw new IllegalStateException("seats " + alreadySold + " are already sold; refund required");
        }

        show.markBooked(booking.seatIds());
        lockProvider.unlockSeats(booking.showId(), booking.seatIds(), booking.userId());
        booking.transitionTo(BookingStatus.CONFIRMED);
        return booking;
    }

    /** Abandoning checkout: release the hold immediately rather than waiting for the TTL. */
    public synchronized void abandon(String bookingId) {
        Booking booking = booking(bookingId).orElseThrow();
        if (booking.status() == BookingStatus.PENDING_PAYMENT) {
            lockProvider.unlockSeats(booking.showId(), booking.seatIds(), booking.userId());
            booking.transitionTo(BookingStatus.EXPIRED);
        }
    }

    /** Cancelling a confirmed booking returns the seats and leaves a refund obligation. */
    public synchronized void cancel(String bookingId) {
        Booking booking = booking(bookingId).orElseThrow();
        if (booking.status() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("only confirmed bookings can be cancelled");
        }
        show(booking.showId()).releaseBooked(booking.seatIds());
        booking.transitionTo(BookingStatus.CANCELLED);
    }

    /**
     * What the seat map should show a browsing user: not sold, and not held by anyone.
     *
     * <p>Held seats are hidden even though nobody owns them yet. Showing them would send users into
     * a checkout that fails a second later, which is worse than showing fewer seats.
     */
    public List<Seat> availableSeats(String showId) {
        Show show = show(showId);
        return show.allSeats().stream()
                .filter(seat -> !show.isBooked(seat.id()))
                .filter(seat -> !lockProvider.isLocked(showId, seat.id()))
                .toList();
    }

    public Optional<Booking> booking(String bookingId) {
        return Optional.ofNullable(bookings.get(bookingId));
    }
}
