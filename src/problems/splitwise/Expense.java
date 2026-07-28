package problems.splitwise;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable record of one expense.
 *
 * <p><b>Shares are computed once, at creation, and stored.</b> The alternative — keeping the
 * strategy on the expense and recomputing on demand — looks tidier but is wrong: if the split rule
 * or the participant list changes tomorrow, every historical expense silently re-splits and your
 * ledger stops matching what people actually agreed to. Store the outcome, not the recipe.
 *
 * <p>This is the same reason invoices store the price paid rather than a pointer to the product's
 * current price.
 */
public record Expense(String id,
                      String description,
                      BigDecimal total,
                      String paidBy,
                      Map<String, BigDecimal> shares,
                      String splitType) {

    public Expense {
        Objects.requireNonNull(id);
        Objects.requireNonNull(paidBy);
        if (total.signum() <= 0) {
            throw new IllegalArgumentException("An expense must be positive");
        }
        shares = Map.copyOf(shares);

        BigDecimal sum = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) != 0) {
            // Invariant enforced at the boundary: a badly written strategy cannot corrupt the ledger.
            throw new IllegalArgumentException("Shares sum to " + sum + " but total is " + total);
        }
    }

    public List<String> participants() {
        return List.copyOf(shares.keySet());
    }

    @Override
    public String toString() {
        return String.format("%-22s Rs.%-9s paid by %-6s [%s]", description, total, paidBy, splitType);
    }
}
