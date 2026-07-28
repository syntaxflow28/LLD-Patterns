package problems.booking;

/**
 * One physical seat in an auditorium.
 *
 * <p><b>Why a seat carries no availability flag.</b> The instinct is {@code boolean isBooked} on the
 * seat, and it is wrong for two reasons. First, a seat is reused by every show in that auditorium —
 * A5 is free for the 9pm screening and taken for the 6pm one, so availability belongs to the
 * (show, seat) pair and lives on {@link Show}. Second, a mutable flag on a shared object is exactly
 * the thing twenty concurrent booking threads will corrupt. Keeping {@code Seat} an immutable value
 * removes a whole class of race conditions by construction.
 *
 * <p>Getting this separation right early is what makes the concurrency section of this problem
 * tractable rather than a mess of synchronised setters.
 */
public record Seat(String id, int row, int number, SeatType type) {

    public static Seat of(char row, int number, SeatType type) {
        return new Seat(row + String.valueOf(number), row - 'A' + 1, number, type);
    }
}
