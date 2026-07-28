package problems.vendingmachine;

import java.util.List;
import java.util.Objects;

/**
 * The CONTEXT of the state machine, and the FACADE the keypad/coin-slot hardware talks to.
 *
 * <p>Notice how little logic lives here. Every public method is a one-line delegation to the
 * current state; the package-private methods below are the primitives states are allowed to use.
 * That split is the whole design: <em>the machine owns the data, the states own the rules.</em>
 *
 * <p><b>Concurrency.</b> A vending machine is physically single-user, so a coarse lock is the right
 * call — the correct answer here is "make it simple", not "make it lock-free". The methods are
 * {@code synchronized} so a maintenance thread restocking cannot interleave with a purchase.
 * Volunteering that you chose the <em>cheapest correct</em> synchronisation, and why, reads better
 * than reaching for concurrent collections you do not need.
 */
public class VendingMachine {

    private final Inventory inventory;
    private final CoinBank bank;

    private VendingState state;
    private int balance;
    private String selectedCode;

    public VendingMachine(Inventory inventory, CoinBank bank) {
        this.inventory = Objects.requireNonNull(inventory);
        this.bank = Objects.requireNonNull(bank);
        this.state = inventory.isSoldOut() ? OutOfServiceState.INSTANCE : IdleState.INSTANCE;
    }

    // ---------------------------------------------------------------- public API

    public synchronized void insertCoin(Coin coin) {
        state.insertCoin(this, coin);
    }

    public synchronized void selectItem(String code) {
        state.selectItem(this, code);
    }

    public synchronized Purchase dispense() {
        return state.dispense(this);
    }

    public synchronized List<Coin> refund() {
        return state.refund(this);
    }

    /** Operator action: reload a slot and bring the machine back online. */
    public synchronized void restock(Item item, int qty) {
        inventory.load(item, qty);
        if (state == OutOfServiceState.INSTANCE && balance == 0) {
            state = IdleState.INSTANCE;
        }
    }

    public synchronized String stateName() {
        return state.name();
    }

    public synchronized int balance() {
        return balance;
    }

    // ---------------------------------------------------------------- primitives for states

    Inventory inventory() {
        return inventory;
    }

    CoinBank bank() {
        return bank;
    }

    void setState(VendingState next) {
        this.state = next;
    }

    /**
     * Coins go straight into the hopper, not into an escrow tray.
     *
     * <p>That ordering matters: the Rs.50 the customer just inserted is immediately available as
     * change for the <em>next</em> customer, and — more importantly — the "can I make change?"
     * check in HAS_MONEY sees a truthful hopper. Holding coins in escrow and depositing them only
     * on success makes the check lie.
     */
    void acceptCoin(Coin coin) {
        bank.deposit(coin);
        balance += coin.value();
    }

    void select(String code) {
        this.selectedCode = code;
    }

    List<Coin> refundBalance() {
        List<Coin> coins = bank.withdraw(balance)
                .orElseThrow(() -> new IllegalStateException("Hopper jam: cannot refund Rs." + balance));
        balance = 0;
        selectedCode = null;
        setState(inventory.isSoldOut() ? OutOfServiceState.INSTANCE : IdleState.INSTANCE);
        return coins;
    }

    Purchase completeSale() {
        Item item = inventory.find(selectedCode)
                .orElseThrow(() -> new IllegalStateException("Nothing selected"));

        inventory.decrement(item.code());
        int change = balance - item.price();
        List<Coin> coins = bank.withdraw(change)
                .orElseThrow(() -> new IllegalStateException("Hopper jam: cannot pay Rs." + change));

        balance = 0;
        selectedCode = null;
        setState(inventory.isSoldOut() ? OutOfServiceState.INSTANCE : IdleState.INSTANCE);
        return new Purchase(item, coins);
    }

    /** What actually falls out of the machine. */
    public record Purchase(Item item, List<Coin> change) {

        @Override
        public String toString() {
            int total = change.stream().mapToInt(Coin::value).sum();
            return item.name() + " + Rs." + total + " change " + change;
        }
    }
}
