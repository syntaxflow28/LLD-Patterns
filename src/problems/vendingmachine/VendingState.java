package problems.vendingmachine;

import java.util.List;

/**
 * STATE — the behaviour of every operation depends on which state the machine is in.
 *
 * <p><b>Why State and not Strategy.</b> They have the same class diagram, so interviewers use this
 * problem to find out whether you know the difference. With Strategy, the <em>client</em> picks the
 * algorithm and the strategy never swaps itself. Here the machine transitions <em>itself</em>:
 * inserting a coin moves IDLE &rarr; HAS_MONEY without anyone asking. State objects know their
 * successors; strategies do not.
 *
 * <p><b>Why not an enum plus a switch.</b> That is the alternative you should mention and reject.
 * With four operations and four states you get four switch blocks that must all be kept in sync,
 * and adding a fifth state means editing every one of them. Here a new state is one new class.
 *
 * <p>The {@code default} methods make illegal transitions the norm: each state overrides only what
 * it permits, and everything else is rejected with a message that names the current state. This is
 * the trick that keeps the state classes tiny.
 */
public interface VendingState {

    String name();

    default void insertCoin(VendingMachine machine, Coin coin) {
        throw notAllowed("insert coin");
    }

    default void selectItem(VendingMachine machine, String code) {
        throw notAllowed("select item");
    }

    default VendingMachine.Purchase dispense(VendingMachine machine) {
        throw notAllowed("dispense");
    }

    default List<Coin> refund(VendingMachine machine) {
        throw notAllowed("refund");
    }

    private IllegalStateException notAllowed(String operation) {
        return new IllegalStateException("Cannot " + operation + " while machine is " + name());
    }
}
