package com.lld.problems.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A screening: one movie, one screen, one start time, and the seat map for it.
 *
 * <p><b>This is where "booked" lives.</b> {@link Seat} is a reusable immutable value; whether it is
 * taken is a fact about this particular screening. The distinction matters the moment the same
 * auditorium runs four shows a day.
 *
 * <p><b>Why {@code bookedSeatIds} is a concurrent set and not a {@code HashSet}.</b> Confirmations
 * arrive from many request threads. A plain {@code HashSet} under concurrent {@code add} can corrupt
 * its internal table — not merely lose an entry, but livelock on a resize. Note carefully what this
 * set does and does not give you: {@code add} returning {@code false} atomically tells you the seat
 * was already booked, which is a real guarantee. It does <em>not</em> make "check availability, then
 * book" atomic — that compound operation is what {@link SeatLockProvider} exists to protect.
 */
public class Show {

    private final String id;
    private final String movieTitle;
    private final String screen;
    private final Instant startTime;
    private final BigDecimal basePrice;
    private final Map<String, Seat> seats;
    private final Set<String> bookedSeatIds = ConcurrentHashMap.newKeySet();

    public Show(String id, String movieTitle, String screen, Instant startTime,
                BigDecimal basePrice, List<Seat> seats) {
        this.id = id;
        this.movieTitle = movieTitle;
        this.screen = screen;
        this.startTime = startTime;
        this.basePrice = basePrice;

        // LinkedHashMap wrapped as unmodifiable, NOT Map.copyOf: Map.copyOf makes no ordering
        // promise and will happily hand back rows in the order B, A, C. Immutability and iteration
        // order are separate guarantees, and this class needs both.
        Map<String, Seat> byId = new LinkedHashMap<>();
        for (Seat seat : seats) {
            byId.put(seat.id(), seat);
        }
        this.seats = Collections.unmodifiableMap(byId);
    }

    /**
     * Builds a rectangular auditorium: front rows platinum, middle gold, back silver.
     *
     * <p>A convenience for demos and tests. In production this comes from the venue's stored layout,
     * which is rarely rectangular — aisles, wheelchair spaces and boxes all break the grid, which is
     * why the real model is a list of seats rather than a 2D array.
     */
    public static List<Seat> layout(int rows, int seatsPerRow) {
        List<Seat> seats = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            char rowLabel = (char) ('A' + r);
            SeatType type = r == 0 ? SeatType.PLATINUM : r < rows - 1 ? SeatType.GOLD : SeatType.SILVER;
            for (int n = 1; n <= seatsPerRow; n++) {
                seats.add(Seat.of(rowLabel, n, type));
            }
        }
        return seats;
    }

    public Optional<Seat> seat(String seatId) {
        return Optional.ofNullable(seats.get(seatId));
    }

    public List<Seat> allSeats() {
        return List.copyOf(seats.values());
    }

    public boolean isBooked(String seatId) {
        return bookedSeatIds.contains(seatId);
    }

    /**
     * Marks seats as permanently sold. Package-private on purpose: only {@link BookingService} may
     * call it, and only after payment has cleared. Making this public would let any caller book a
     * seat without going through the lock, which is the whole safety mechanism.
     */
    void markBooked(List<String> seatIds) {
        bookedSeatIds.addAll(seatIds);
    }

    /** Cancellation returns seats to the pool. */
    void releaseBooked(List<String> seatIds) {
        seatIds.forEach(bookedSeatIds::remove);
    }

    public String id() {
        return id;
    }

    public String movieTitle() {
        return movieTitle;
    }

    public String screen() {
        return screen;
    }

    public Instant startTime() {
        return startTime;
    }

    public BigDecimal basePrice() {
        return basePrice;
    }

    @Override
    public String toString() {
        return movieTitle + " @ " + screen + " " + startTime + " (" + seats.size() + " seats)";
    }
}
