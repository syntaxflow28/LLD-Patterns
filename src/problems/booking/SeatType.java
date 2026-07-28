package problems.booking;

import java.math.BigDecimal;

/**
 * Seat tiers and what they cost relative to the show's base price.
 *
 * <p><b>Why a multiplier and not an absolute price.</b> Absolute prices per tier means a price rise
 * has to be applied in three places and can drift out of step. A base price on the show times a
 * multiplier on the tier keeps one number to change, and makes "gold is 1.5x silver" an explicit,
 * testable fact rather than an emergent property of two constants.
 *
 * <p><b>Why {@link BigDecimal} and not {@code double}.</b> Money. {@code 0.1 + 0.2 != 0.3} in binary
 * floating point, and a cinema doing a million transactions a day will notice. Using {@code double}
 * for currency is the single fastest way to lose points in an LLD interview.
 */
public enum SeatType {

    SILVER(new BigDecimal("1.0")),
    GOLD(new BigDecimal("1.5")),
    PLATINUM(new BigDecimal("2.0"));

    private final BigDecimal multiplier;

    SeatType(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public BigDecimal multiplier() {
        return multiplier;
    }
}
