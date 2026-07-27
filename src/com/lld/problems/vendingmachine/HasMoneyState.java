package com.lld.problems.vendingmachine;

import java.util.List;

/**
 * Money is in, nothing selected yet. Customer may add more coins, pick an item, or walk away.
 *
 * <p>All four purchase preconditions are validated <em>here</em>, before any state change:
 * slot exists, slot has stock, enough money, and — the one people miss — the machine can actually
 * pay the change. Failing early means the machine never gets stuck holding a snack it cannot
 * complete the sale for.
 */
public final class HasMoneyState implements VendingState {

    public static final VendingState INSTANCE = new HasMoneyState();

    private HasMoneyState() {
    }

    @Override
    public String name() {
        return "HAS_MONEY";
    }

    @Override
    public void insertCoin(VendingMachine machine, Coin coin) {
        machine.acceptCoin(coin);
    }

    @Override
    public void selectItem(VendingMachine machine, String code) {
        Item item = machine.inventory().find(code)
                .orElseThrow(() -> new IllegalArgumentException("No such slot: " + code));

        if (machine.inventory().stock(code) == 0) {
            throw new IllegalStateException("Sold out: " + item.name());
        }
        if (machine.balance() < item.price()) {
            throw new IllegalStateException("Insufficient funds: " + item.name()
                    + " costs Rs." + item.price() + ", balance is Rs." + machine.balance());
        }

        int change = machine.balance() - item.price();
        if (!machine.bank().canMake(change)) {
            throw new IllegalStateException("Cannot return Rs." + change
                    + " in change - insert exact amount or take a refund");
        }

        machine.select(code);
        machine.setState(DispensingState.INSTANCE);
    }

    @Override
    public List<Coin> refund(VendingMachine machine) {
        return machine.refundBalance();
    }
}
