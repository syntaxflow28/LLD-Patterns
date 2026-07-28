package problems.splitwise;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * The ledger: who owes whom, right now.
 *
 * <p><b>The key design decision in this problem.</b> The naive answer recomputes balances by
 * replaying every expense on every read — O(expenses) per query, and it gets slower forever. Here
 * the ledger is a materialised view kept up to date on write, so reads are O(1) per pair.
 *
 * <p>Structure is a nested map: {@code ledger[a][b] > 0} means <em>a owes b</em>. Both directions
 * are stored and kept antisymmetric ({@code ledger[b][a] == -ledger[a][b]}), which makes lookups
 * from either person's point of view a single map hit with no sign juggling at the call site. The
 * cost is 2&times; memory — a trade-off worth naming out loud.
 *
 * <p>Note there is no "Group" here. Balances are strictly pairwise; a group is a UI grouping of
 * expenses, not a unit of debt. Candidates who model debt at the group level end up unable to
 * answer "what does Alice owe Bob across all groups?".
 */
public class BalanceSheet {

    private final Map<String, Map<String, BigDecimal>> ledger = new HashMap<>();

    /** Records that {@code debtor} now owes {@code creditor} an extra {@code amount}. */
    public void record(String debtor, String creditor, BigDecimal amount) {
        if (debtor.equals(creditor) || amount.signum() == 0) {
            return; // you never owe yourself
        }
        add(debtor, creditor, amount);
        add(creditor, debtor, amount.negate());
    }

    private void add(String from, String to, BigDecimal amount) {
        ledger.computeIfAbsent(from, k -> new HashMap<>())
                .merge(to, amount, BigDecimal::add);
    }

    /** Positive result: {@code a} owes {@code b}. Negative: {@code b} owes {@code a}. */
    public BigDecimal between(String a, String b) {
        return ledger.getOrDefault(a, Map.of()).getOrDefault(b, BigDecimal.ZERO);
    }

    /** Every non-zero line for one person, keyed by counterparty. */
    public Map<String, BigDecimal> statementFor(String user) {
        Map<String, BigDecimal> lines = new TreeMap<>();
        ledger.getOrDefault(user, Map.of()).forEach((other, amount) -> {
            if (amount.signum() != 0) {
                lines.put(other, amount);
            }
        });
        return lines;
    }

    /**
     * Net position per person: positive means the group owes them, negative means they owe.
     * These always sum to zero, which is a cheap invariant to assert in tests.
     */
    public Map<String, BigDecimal> netPositions() {
        Map<String, BigDecimal> net = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, BigDecimal>> row : ledger.entrySet()) {
            BigDecimal owed = row.getValue().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            net.put(row.getKey(), owed.negate());
        }
        return net;
    }
}
