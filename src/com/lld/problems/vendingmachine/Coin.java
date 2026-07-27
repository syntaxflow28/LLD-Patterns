package com.lld.problems.vendingmachine;

/**
 * Accepted denominations, in rupees.
 *
 * <p>Money is an {@code int} here, not {@link java.math.BigDecimal}. That is deliberate and worth
 * justifying out loud: a vending machine deals in whole discrete coins, so the smallest unit is
 * exact and there is no division. The rule is "never use floating point for money" — integers of
 * the minor unit are fine, and are what real payment systems use.
 */
public enum Coin {

    FIVE(5),
    TEN(10),
    TWENTY(20),
    FIFTY(50);

    private final int value;

    Coin(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
