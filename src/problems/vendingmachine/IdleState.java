package problems.vendingmachine;

/**
 * Waiting for a customer. The only legal move is to put money in.
 *
 * <p>States hold no per-machine data, so a single shared instance serves every machine — the same
 * reasoning as Flyweight. If a state ever needs mutable data, that data belongs on the machine.
 */
public final class IdleState implements VendingState {

    public static final VendingState INSTANCE = new IdleState();

    private IdleState() {
    }

    @Override
    public String name() {
        return "IDLE";
    }

    @Override
    public void insertCoin(VendingMachine machine, Coin coin) {
        machine.acceptCoin(coin);
        machine.setState(HasMoneyState.INSTANCE);
    }
}
