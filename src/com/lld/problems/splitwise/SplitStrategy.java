package com.lld.problems.splitwise;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * STRATEGY — how is one expense divided among its participants?
 *
 * <p>This is the axis the whole problem turns on. EQUAL / EXACT / PERCENT are the three the
 * interviewer names, and SHARES (weights) is the one they add as a follow-up to see whether your
 * design absorbs it. Here it costs one new class and zero edits anywhere else.
 *
 * <p><b>The pattern-matching answer to avoid.</b> A {@code SplitType} enum plus a {@code switch}
 * inside {@code ExpenseService} works, but every new split type edits the service — and the service
 * is the class you least want to touch, because it also owns the ledger.
 *
 * <p>Every implementation must guarantee the shares sum <em>exactly</em> to the total. Rs.100 split
 * three ways is not 33.33 &times; 3 = 99.99; someone has to absorb the extra paisa, and the design
 * has to say who. Silently losing money is the classic bug in this problem.
 */
public interface SplitStrategy {

    /**
     * @param total        the full amount of the expense
     * @param participants ordered participant ids
     * @param inputs       strategy-specific numbers (exact amounts, percentages, weights);
     *                     ignored by EQUAL
     * @return participant id &rarr; the amount they are responsible for, summing exactly to total
     */
    Map<String, BigDecimal> shares(BigDecimal total, List<String> participants, List<BigDecimal> inputs);

    String name();

    /**
     * Split evenly, giving the leftover paisa to the earliest participants.
     *
     * <p>"First few pay the extra paisa" is an arbitrary but <em>stated</em> rule. Interviewers do
     * not care which rule you pick; they care that you noticed the problem and made the choice
     * explicit rather than letting {@code RoundingMode.HALF_UP} quietly invent money.
     */
    final class Equal implements SplitStrategy {

        @Override
        public Map<String, BigDecimal> shares(BigDecimal total, List<String> participants,
                                              List<BigDecimal> inputs) {
            int n = participants.size();
            BigDecimal base = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.DOWN);
            BigDecimal distributed = base.multiply(BigDecimal.valueOf(n));

            // How many paise are still unallocated after rounding everyone down?
            int leftoverPaise = total.subtract(distributed).movePointRight(2).intValueExact();

            Map<String, BigDecimal> result = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) {
                BigDecimal share = i < leftoverPaise ? base.add(new BigDecimal("0.01")) : base;
                result.put(participants.get(i), share);
            }
            return result;
        }

        @Override
        public String name() {
            return "EQUAL";
        }
    }

    /** Caller states each person's amount. Validated to sum to the total — no silent drift. */
    final class Exact implements SplitStrategy {

        @Override
        public Map<String, BigDecimal> shares(BigDecimal total, List<String> participants,
                                              List<BigDecimal> inputs) {
            require(participants.size() == inputs.size(), "Need one amount per participant");

            BigDecimal sum = inputs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            require(sum.compareTo(total) == 0,
                    "Exact amounts sum to " + sum + " but the expense is " + total);

            Map<String, BigDecimal> result = new LinkedHashMap<>();
            for (int i = 0; i < participants.size(); i++) {
                result.put(participants.get(i), inputs.get(i).setScale(2, RoundingMode.HALF_UP));
            }
            return result;
        }

        @Override
        public String name() {
            return "EXACT";
        }
    }

    /**
     * Caller states percentages summing to 100.
     *
     * <p>Same rounding hazard as EQUAL — 3 &times; 33.33% of Rs.100 loses a paisa — so the
     * remainder is pushed onto the last participant after everyone else is rounded down.
     */
    final class Percent implements SplitStrategy {

        @Override
        public Map<String, BigDecimal> shares(BigDecimal total, List<String> participants,
                                              List<BigDecimal> inputs) {
            require(participants.size() == inputs.size(), "Need one percentage per participant");

            BigDecimal sum = inputs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            require(sum.compareTo(BigDecimal.valueOf(100)) == 0,
                    "Percentages sum to " + sum + ", must be 100");

            Map<String, BigDecimal> result = new LinkedHashMap<>();
            BigDecimal allocated = BigDecimal.ZERO;
            for (int i = 0; i < participants.size() - 1; i++) {
                BigDecimal share = total.multiply(inputs.get(i))
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN);
                result.put(participants.get(i), share);
                allocated = allocated.add(share);
            }
            result.put(participants.get(participants.size() - 1), total.subtract(allocated));
            return result;
        }

        @Override
        public String name() {
            return "PERCENT";
        }
    }

    /**
     * Weights, e.g. a flat shared 2:1:1 by room size. Added later without touching anything else —
     * that is the payoff you point at when the interviewer asks "why not just an enum?".
     */
    final class ByShares implements SplitStrategy {

        @Override
        public Map<String, BigDecimal> shares(BigDecimal total, List<String> participants,
                                              List<BigDecimal> inputs) {
            require(participants.size() == inputs.size(), "Need one weight per participant");

            BigDecimal totalWeight = inputs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            require(totalWeight.signum() > 0, "Weights must be positive");

            Map<String, BigDecimal> result = new LinkedHashMap<>();
            BigDecimal allocated = BigDecimal.ZERO;
            for (int i = 0; i < participants.size() - 1; i++) {
                BigDecimal share = total.multiply(inputs.get(i))
                        .divide(totalWeight, 2, RoundingMode.DOWN);
                result.put(participants.get(i), share);
                allocated = allocated.add(share);
            }
            result.put(participants.get(participants.size() - 1), total.subtract(allocated));
            return result;
        }

        @Override
        public String name() {
            return "SHARES";
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
