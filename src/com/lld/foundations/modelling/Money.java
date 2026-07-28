package com.lld.foundations.modelling;

import java.util.Currency;

/**
 * A VALUE OBJECT, and the reason value objects exist.
 *
 * <p>{@code amount} and {@code currency} always travel together, and there is a rule about them that
 * has nowhere to live when they are two loose parameters: <b>you cannot add rupees to dollars</b>.
 * Passing {@code (long amount, String currency)} around means every caller is trusted to check that
 * rule, which means one of them eventually will not, and the bug is silent - the arithmetic succeeds
 * and produces a number that means nothing.
 *
 * <p>Say the field names out loud together. If they form a phrase - "money", "a time slot", "an
 * address" - the phrase is the class you are missing.
 *
 * <p><b>Why a record.</b> Immutability means a {@code Money} handed to a caller cannot be mutated
 * behind the owner's back, and equality by value is what "two 500-rupee amounts are the same amount"
 * actually means. Contrast an entity, where identity survives changes to the data: change a booking's
 * price and it is still that booking; change a money's amount and it is a different money.
 */
public record Money(long minorUnits, Currency currency) {

    public Money {
        if (currency == null) {
            throw new IllegalArgumentException("currency is required");
        }
    }

    public static Money of(String currencyCode, long majorUnits, int minorUnits) {
        return new Money(majorUnits * 100 + minorUnits, Currency.getInstance(currencyCode));
    }

    /**
     * The rule that had nowhere to live before this class existed.
     *
     * <p>This throw is the whole point. It is not defensive programming - it is a state the domain
     * says is meaningless, refusing to be constructed.
     */
    public Money plus(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "cannot add " + other.currency.getCurrencyCode() + " to " + currency.getCurrencyCode());
        }
        return new Money(minorUnits + other.minorUnits, currency);
    }

    @Override
    public String toString() {
        return String.format("%s %d.%02d", currency.getCurrencyCode(), minorUnits / 100, Math.abs(minorUnits % 100));
    }
}
