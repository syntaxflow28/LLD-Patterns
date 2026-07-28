package com.lld.problems.splitwise;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * "Simplify debts" — turn a tangle of pairwise IOUs into the fewest transfers that settle everyone.
 *
 * <p><b>This is the follow-up that separates candidates.</b> Almost everyone gets the split
 * strategies; far fewer notice that A&rarr;B Rs.500, B&rarr;C Rs.500 should collapse to A&rarr;C
 * Rs.500 and that B should not have to move any money at all.
 *
 * <p><b>The algorithm.</b> Compute each person's net position (it must sum to zero). Repeatedly
 * match the largest debtor with the largest creditor and transfer the smaller of the two amounts.
 * Each transfer zeroes out at least one person, so it terminates in at most n-1 transfers, which is
 * also the theoretical floor for any connected group.
 *
 * <p><b>Say this out loud:</b> greedy is a <em>heuristic</em>, not an optimum. Finding the true
 * minimum number of transfers requires partitioning the group into zero-sum subsets, which is
 * NP-hard (it contains subset-sum). Greedy gives at most n-1 transfers and is what production
 * systems ship. Naming the complexity class unprompted is a strong senior signal; claiming greedy
 * is optimal is a correctness error the interviewer will probe.
 */
public final class DebtSimplifier {

    private DebtSimplifier() {
    }

    /** One transfer: {@code from} pays {@code to}. */
    public record Settlement(String from, String to, BigDecimal amount) {

        @Override
        public String toString() {
            return from + " pays " + to + " Rs." + amount;
        }
    }

    public static List<Settlement> minimize(Map<String, BigDecimal> netPositions) {
        // Max-heap of creditors (owed the most) and max-heap of debtors (owe the most).
        Comparator<Map.Entry<String, BigDecimal>> byAmountDesc =
                Map.Entry.<String, BigDecimal>comparingByValue().reversed();

        PriorityQueue<Map.Entry<String, BigDecimal>> creditors = new PriorityQueue<>(byAmountDesc);
        PriorityQueue<Map.Entry<String, BigDecimal>> debtors =
                new PriorityQueue<>(Map.Entry.<String, BigDecimal>comparingByValue());

        netPositions.forEach((user, net) -> {
            if (net.signum() > 0) {
                creditors.add(Map.entry(user, net));
            } else if (net.signum() < 0) {
                debtors.add(Map.entry(user, net));
            }
        });

        List<Settlement> settlements = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            Map.Entry<String, BigDecimal> creditor = creditors.poll();
            Map.Entry<String, BigDecimal> debtor = debtors.poll();

            BigDecimal transfer = creditor.getValue().min(debtor.getValue().negate());
            settlements.add(new Settlement(debtor.getKey(), creditor.getKey(), transfer));

            // Whoever still has a residue goes back on the heap; at least one is now zero.
            BigDecimal creditorLeft = creditor.getValue().subtract(transfer);
            BigDecimal debtorLeft = debtor.getValue().add(transfer);

            if (creditorLeft.signum() > 0) {
                creditors.add(Map.entry(creditor.getKey(), creditorLeft));
            }
            if (debtorLeft.signum() < 0) {
                debtors.add(Map.entry(debtor.getKey(), debtorLeft));
            }
        }
        return settlements;
    }
}
