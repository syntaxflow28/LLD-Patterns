package problems.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * STRATEGY (+ DECORATOR) — what a seat costs.
 *
 * <p>Pricing is the requirement that changes weekly while nothing else does: weekend surge, Tuesday
 * discount, a promo code, loyalty tiers. That volatility is the textbook signal for Strategy — it is
 * the single axis of this design most likely to need a new rule next sprint, and the one you should
 * isolate first.
 *
 * <p><b>Why the modifiers are decorators rather than more strategies.</b> Real pricing composes:
 * a weekend surge <em>and</em> a 10% coupon apply together. Flat strategies would need a
 * {@code WeekendSurgeWithCouponPricing} class for every combination — the same M x N explosion the
 * notification service avoids with Bridge. Wrapping means
 * {@code new PercentOff(new Surge(new BySeatType(), 1.25), 10)} reads in the order the rules apply.
 *
 * <p><b>The follow-up worth pre-empting:</b> order matters. Applying a coupon before surge gives a
 * different number than after, and the business has an opinion about which is correct. Point at the
 * nesting order and say "this applies surge first, then the discount" — that shows you know the
 * composition is meaningful rather than decorative.
 */
public interface PricingStrategy {

    BigDecimal priceFor(Show show, Seat seat);

    String name();

    /** Rounds to paise/cents at each layer, so the printed total always matches the sum of parts. */
    private static BigDecimal round(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** The base rule: show price x seat tier multiplier. */
    final class BySeatType implements PricingStrategy {

        @Override
        public BigDecimal priceFor(Show show, Seat seat) {
            return round(show.basePrice().multiply(seat.type().multiplier()));
        }

        @Override
        public String name() {
            return "base";
        }
    }

    /** DECORATOR: multiplies whatever the wrapped strategy charged. Weekends, opening night. */
    final class Surge implements PricingStrategy {

        private final PricingStrategy delegate;
        private final BigDecimal multiplier;

        public Surge(PricingStrategy delegate, BigDecimal multiplier) {
            this.delegate = delegate;
            this.multiplier = multiplier;
        }

        @Override
        public BigDecimal priceFor(Show show, Seat seat) {
            return round(delegate.priceFor(show, seat).multiply(multiplier));
        }

        @Override
        public String name() {
            return delegate.name() + " +surge" + multiplier + "x";
        }
    }

    /** DECORATOR: a percentage off. Coupons, loyalty tiers, matinee pricing. */
    final class PercentOff implements PricingStrategy {

        private final PricingStrategy delegate;
        private final int percent;

        public PercentOff(PricingStrategy delegate, int percent) {
            if (percent < 0 || percent > 100) {
                throw new IllegalArgumentException("percent must be 0..100");
            }
            this.delegate = delegate;
            this.percent = percent;
        }

        @Override
        public BigDecimal priceFor(Show show, Seat seat) {
            BigDecimal full = delegate.priceFor(show, seat);
            BigDecimal discount = full.multiply(BigDecimal.valueOf(percent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return round(full.subtract(discount));
        }

        @Override
        public String name() {
            return delegate.name() + " -" + percent + "%";
        }
    }
}
