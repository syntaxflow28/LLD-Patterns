package problems.vendingmachine;

import java.util.List;

/**
 * Everything is sold out (or an operator has taken the machine offline).
 *
 * <p>Refund stays legal: if a customer's money is already inside when the last item goes, the
 * machine must still be able to give it back. Getting that right is the difference between "I know
 * the pattern" and "I thought about the user".
 */
public final class OutOfServiceState implements VendingState {

    public static final VendingState INSTANCE = new OutOfServiceState();

    private OutOfServiceState() {
    }

    @Override
    public String name() {
        return "OUT_OF_SERVICE";
    }

    @Override
    public List<Coin> refund(VendingMachine machine) {
        return machine.refundBalance();
    }
}
