package problems.splitwise;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The application service — the FACADE the API layer calls.
 *
 * <p>It orchestrates and validates; it does not calculate. The split maths lives in
 * {@link SplitStrategy}, the ledger maths lives in {@link BalanceSheet}, the settlement maths lives
 * in {@link DebtSimplifier}. That is why this class stays short even as the feature list grows.
 *
 * <p><b>Where the interviewer will push:</b>
 * <ul>
 *   <li>"Two people add an expense to the same group at once" &rarr; the ledger update must be
 *       atomic with the expense insert. In a real system that is one DB transaction; here the
 *       method is {@code synchronized}.</li>
 *   <li>"How do you show a user's balance fast?" &rarr; materialised ledger, O(1) per pair.</li>
 *   <li>"Notify people when they're added to an expense" &rarr; Observer; the hook is
 *       {@link ExpenseListener} below.</li>
 * </ul>
 */
public class ExpenseService {

    private final Map<String, User> users = new LinkedHashMap<>();
    private final List<Expense> expenses = new ArrayList<>();
    private final BalanceSheet balances = new BalanceSheet();
    private final List<ExpenseListener> listeners = new ArrayList<>();
    private final AtomicInteger sequence = new AtomicInteger();

    /** OBSERVER hook: notifications, activity feed, analytics — none of which this class knows about. */
    public interface ExpenseListener {
        void onExpenseAdded(Expense expense);
    }

    public void addUser(User user) {
        users.put(user.id(), user);
    }

    public void addListener(ExpenseListener listener) {
        listeners.add(listener);
    }

    /**
     * Records an expense and updates the ledger in one step.
     *
     * @param inputs strategy-specific numbers; pass {@code List.of()} for an equal split
     */
    public synchronized Expense addExpense(String description,
                                           BigDecimal total,
                                           String paidBy,
                                           List<String> participants,
                                           SplitStrategy strategy,
                                           List<BigDecimal> inputs) {
        requireKnown(paidBy);
        participants.forEach(this::requireKnown);
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("An expense needs at least one participant");
        }

        Map<String, BigDecimal> shares = strategy.shares(total, participants, inputs);
        Expense expense = new Expense(
                "E" + sequence.incrementAndGet(), description, total, paidBy, shares, strategy.name());

        // The payer fronted the money, so everyone else owes them their share.
        shares.forEach((participant, share) -> balances.record(participant, paidBy, share));

        expenses.add(expense);
        listeners.forEach(listener -> listener.onExpenseAdded(expense));
        return expense;
    }

    /** A real payment between two people. Modelled as a normal ledger entry in the other direction. */
    public synchronized void settleUp(String from, String to, BigDecimal amount) {
        requireKnown(from);
        requireKnown(to);
        BigDecimal outstanding = balances.between(from, to);
        if (amount.compareTo(outstanding) > 0) {
            throw new IllegalArgumentException(
                    from + " only owes " + to + " Rs." + outstanding + ", cannot settle Rs." + amount);
        }
        balances.record(to, from, amount);
    }

    public BalanceSheet balances() {
        return balances;
    }

    public List<Expense> expenses() {
        return List.copyOf(expenses);
    }

    public Optional<User> user(String id) {
        return Optional.ofNullable(users.get(id));
    }

    public List<DebtSimplifier.Settlement> simplify() {
        return DebtSimplifier.minimize(balances.netPositions());
    }

    private void requireKnown(String userId) {
        if (!users.containsKey(userId)) {
            throw new IllegalArgumentException("Unknown user: " + userId);
        }
    }
}
