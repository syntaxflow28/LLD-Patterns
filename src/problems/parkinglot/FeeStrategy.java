package problems.parkinglot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * STRATEGY — how much does this session cost?
 *
 * <p>This is the single most important abstraction in the problem. "Pricing changes" is not a
 * hypothetical: weekend rates, EV surcharges, validated parking, monthly passes and airport tariffs
 * all land here. Every one of them is a new class, and {@link ParkingLot} never changes. That is
 * Open/Closed with a concrete pay-off you can point at.
 *
 * <p><b>Money is {@link BigDecimal}, never {@code double}.</b> Say this out loud in the interview —
 * {@code 0.1 + 0.2 != 0.3} in binary floating point, and a parking lot that loses a paisa per exit
 * loses real money at scale.
 */
public interface FeeStrategy {

    BigDecimal fee(Ticket ticket, Instant exitTime);

    /** Human-readable name for receipts and logs. */
    String description();

    /**
     * Flat charge for the first hour, then a per-hour rate that depends on the vehicle.
     * Part hours are rounded up — that is a business rule, so it is stated explicitly rather than
     * hidden in integer division.
     */
    final class HourlyTiered implements FeeStrategy {

        private final BigDecimal firstHourFlat;
        private final Map<VehicleType, BigDecimal> hourlyRate;

        public HourlyTiered(BigDecimal firstHourFlat, Map<VehicleType, BigDecimal> hourlyRate) {
            this.firstHourFlat = Objects.requireNonNull(firstHourFlat);
            this.hourlyRate = Map.copyOf(hourlyRate);
        }

        @Override
        public BigDecimal fee(Ticket ticket, Instant exitTime) {
            long hours = billableHours(ticket.entryTime(), exitTime);
            BigDecimal rate = hourlyRate.get(ticket.vehicle().type());
            if (rate == null) {
                throw new IllegalStateException("No rate configured for " + ticket.vehicle().type());
            }
            BigDecimal extra = rate.multiply(BigDecimal.valueOf(hours - 1));
            return firstHourFlat.add(extra).setScale(2, RoundingMode.HALF_UP);
        }

        /** Always at least one hour; every started hour is billed. */
        private long billableHours(Instant entry, Instant exit) {
            long minutes = Duration.between(entry, exit).toMinutes();
            return Math.max(1, (minutes + 59) / 60);
        }

        @Override
        public String description() {
            return "Hourly (flat first hour " + firstHourFlat + ")";
        }
    }

    /** Flat charge per started calendar day — the "event parking" tariff. */
    final class DayPass implements FeeStrategy {

        private final BigDecimal perDay;

        public DayPass(BigDecimal perDay) {
            this.perDay = Objects.requireNonNull(perDay);
        }

        @Override
        public BigDecimal fee(Ticket ticket, Instant exitTime) {
            long hours = Math.max(1, Duration.between(ticket.entryTime(), exitTime).toHours());
            long days = (hours + 23) / 24;
            return perDay.multiply(BigDecimal.valueOf(days)).setScale(2, RoundingMode.HALF_UP);
        }

        @Override
        public String description() {
            return "Day pass (" + perDay + "/day)";
        }
    }

    /**
     * DECORATOR over any other strategy — applies a percentage discount.
     *
     * <p>Worth volunteering: discounts compose. Wrapping beats adding a {@code discountPercent}
     * field to every strategy, and it lets you stack "staff discount" on top of "weekend rate"
     * without a combinatorial explosion of classes.
     */
    final class PercentDiscount implements FeeStrategy {

        private final FeeStrategy delegate;
        private final BigDecimal percentOff;

        public PercentDiscount(FeeStrategy delegate, BigDecimal percentOff) {
            this.delegate = Objects.requireNonNull(delegate);
            this.percentOff = Objects.requireNonNull(percentOff);
        }

        @Override
        public BigDecimal fee(Ticket ticket, Instant exitTime) {
            BigDecimal base = delegate.fee(ticket, exitTime);
            BigDecimal multiplier = BigDecimal.ONE.subtract(
                    percentOff.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
            return base.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        }

        @Override
        public String description() {
            return delegate.description() + " - " + percentOff + "% off";
        }
    }
}
