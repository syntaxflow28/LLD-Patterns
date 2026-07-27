package com.lld.problems.splitwise;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Runnable walk-through of the Splitwise design.
 *
 * <pre>
 *   java -cp out com.lld.problems.splitwise.SplitwiseDemo
 * </pre>
 *
 * <p>Patterns and the requirement behind each:
 * <ul>
 *   <li><b>Strategy</b> &larr; "equal, exact and percentage splits" (and the SHARES follow-up)</li>
 *   <li><b>Facade / application service</b> &larr; one entry point that keeps expense + ledger consistent</li>
 *   <li><b>Observer</b> &larr; "notify participants"</li>
 *   <li><b>Materialised ledger + greedy min-cash-flow</b> &larr; "show balances fast", "simplify debts"</li>
 * </ul>
 */
public class SplitwiseDemo {

    public static void main(String[] args) {
        ExpenseService service = new ExpenseService();
        service.addUser(new User("alice", "Alice", "alice@x.com"));
        service.addUser(new User("bob", "Bob", "bob@x.com"));
        service.addUser(new User("carol", "Carol", "carol@x.com"));
        service.addUser(new User("dave", "Dave", "dave@x.com"));
        service.addListener(expense -> System.out.println("     NOTIFY | " + expense));

        List<String> everyone = List.of("alice", "bob", "carol", "dave");

        section("1. EQUAL split of Rs.100 three ways - watch the stray paisa");
        Expense dinner = service.addExpense("Dinner", money("100.00"), "alice",
                List.of("alice", "bob", "carol"), new SplitStrategy.Equal(), List.of());
        printShares(dinner);
        System.out.println("  shares sum to exactly Rs." + dinner.total() + " - nothing is invented or lost");

        section("2. EXACT split - caller states each amount");
        Expense groceries = service.addExpense("Groceries", money("500.00"), "bob", everyone,
                new SplitStrategy.Exact(),
                List.of(money("200.00"), money("100.00"), money("150.00"), money("50.00")));
        printShares(groceries);

        section("3. PERCENT split");
        Expense trip = service.addExpense("Goa trip", money("1000.00"), "carol", everyone,
                new SplitStrategy.Percent(),
                List.of(money("40"), money("30"), money("20"), money("10")));
        printShares(trip);

        section("4. SHARES split - added later, zero edits to ExpenseService");
        Expense rent = service.addExpense("Rent (2:1:1:1 by room)", money("30000.00"), "dave", everyone,
                new SplitStrategy.ByShares(),
                List.of(money("2"), money("1"), money("1"), money("1")));
        printShares(rent);

        section("5. Bad input is rejected at the boundary");
        expectFailure("percentages summing to 90", () -> service.addExpense(
                "Bad", money("100.00"), "alice", List.of("alice", "bob"),
                new SplitStrategy.Percent(), List.of(money("40"), money("50"))));
        expectFailure("exact amounts summing to 90", () -> service.addExpense(
                "Bad", money("100.00"), "alice", List.of("alice", "bob"),
                new SplitStrategy.Exact(), List.of(money("40.00"), money("50.00"))));
        expectFailure("unknown user", () -> service.addExpense(
                "Bad", money("100.00"), "eve", List.of("alice"),
                new SplitStrategy.Equal(), List.of()));

        section("6. Statements - pairwise, positive means 'I owe them'");
        for (String user : everyone) {
            System.out.println("  " + user + ": " + service.balances().statementFor(user));
        }

        section("7. Settling up");
        BigDecimal owed = service.balances().between("alice", "bob");
        System.out.println("  alice owes bob Rs." + owed);
        service.settleUp("alice", "bob", money("100.00"));
        System.out.println("  after paying Rs.100 -> Rs." + service.balances().between("alice", "bob"));
        expectFailure("over-paying", () -> service.settleUp("alice", "bob", money("99999.00")));

        section("8. Net positions must sum to zero - the invariant to assert in tests");
        Map<String, BigDecimal> net = new TreeMap<>(service.balances().netPositions());
        net.forEach((user, amount) -> System.out.println(
                "  " + pad(user) + (amount.signum() >= 0 ? "is owed  Rs." : "owes     Rs.")
                        + amount.abs()));
        BigDecimal sum = net.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        System.out.println("  sum = " + sum.stripTrailingZeros().toPlainString());

        section("9. Simplify debts - greedy min-cash-flow, at most n-1 transfers");
        List<DebtSimplifier.Settlement> plan = service.simplify();
        plan.forEach(s -> System.out.println("  " + s));
        System.out.println("  " + plan.size() + " transfers for " + everyone.size()
                + " people (upper bound " + (everyone.size() - 1) + ")");
        System.out.println("  NOTE: these are net transfers, not the pairwise IOUs above - a person");
        System.out.println("        with no direct debt to X can still be told to pay X.");

        System.out.println("\nDone.");
    }

    private static void printShares(Expense expense) {
        System.out.println("  " + expense);
        new TreeMap<>(expense.shares()).forEach(
                (user, share) -> System.out.println("      " + pad(user) + "Rs." + share));
    }

    private static String pad(String user) {
        return String.format("%-8s", user);
    }

    private static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    private static void expectFailure(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + " -> UNEXPECTEDLY ACCEPTED");
        } catch (RuntimeException ex) {
            System.out.println("  " + label + " -> rejected: " + ex.getMessage());
        }
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
