package com.lld.problems.vendingmachine;

import java.util.List;

/**
 * Runnable walk-through of the Vending Machine design.
 *
 * <pre>
 *   java -cp out com.lld.problems.vendingmachine.VendingMachineDemo
 * </pre>
 *
 * <p>The state diagram this code implements:
 *
 * <pre>
 *   IDLE --insertCoin--&gt; HAS_MONEY --selectItem--&gt; DISPENSING --dispense--&gt; IDLE
 *     ^                      |  ^                                              |
 *     |                      |  +--insertCoin--+                               |
 *     +-------refund---------+                                                 |
 *                                                                              v
 *                        OUT_OF_SERVICE &lt;----(last item sold)------------------+
 * </pre>
 *
 * <p>Patterns: <b>State</b> (the whole machine), <b>Strategy-shaped CoinBank</b> for change,
 * <b>Facade</b> for the hardware-facing API, <b>Flyweight-style</b> shared stateless state objects.
 */
public class VendingMachineDemo {

    public static void main(String[] args) {
        Inventory inventory = new Inventory();
        inventory.load(new Item("A1", "Chips", 35), 1);
        inventory.load(new Item("B2", "Soda", 25), 1);

        CoinBank bank = new CoinBank();
        bank.deposit(Coin.FIVE, 2);
        bank.deposit(Coin.TEN, 1);

        VendingMachine machine = new VendingMachine(inventory, bank);

        section("Starting inventory");
        System.out.print(inventory);
        System.out.println("  hopper: " + bank);

        section("1. Illegal transitions are impossible, not merely discouraged");
        System.out.println("  state: " + machine.stateName());
        expectFailure("dispense() from IDLE", machine::dispense);
        expectFailure("selectItem() from IDLE", () -> machine.selectItem("A1"));

        section("2. Happy path: Rs.40 in, Rs.35 chips, Rs.5 back");
        machine.insertCoin(Coin.TWENTY);
        machine.insertCoin(Coin.TWENTY);
        System.out.println("  state: " + machine.stateName() + ", balance Rs." + machine.balance());
        machine.selectItem("A1");
        System.out.println("  state: " + machine.stateName());
        expectFailure("insertCoin() mid-dispense", () -> machine.insertCoin(Coin.FIVE));
        System.out.println("  dispensed: " + machine.dispense());
        System.out.println("  state: " + machine.stateName() + ", hopper: " + bank);

        section("3. Not enough money, then walk away");
        machine.insertCoin(Coin.TEN);
        expectFailure("selectItem(\"B2\") with Rs.10", () -> machine.selectItem("B2"));
        System.out.println("  refunded: " + machine.refund());
        System.out.println("  state: " + machine.stateName());

        section("4. Last item sold -> machine takes itself out of service");
        machine.insertCoin(Coin.TWENTY);
        machine.insertCoin(Coin.TEN);
        machine.selectItem("B2");
        System.out.println("  dispensed: " + machine.dispense());
        System.out.println("  state: " + machine.stateName());
        expectFailure("insertCoin() when OUT_OF_SERVICE", () -> machine.insertCoin(Coin.FIVE));

        section("5. Operator restocks -> back online");
        machine.restock(new Item("A1", "Chips", 35), 3);
        System.out.println("  state: " + machine.stateName());

        section("6. The case candidates forget: the hopper cannot make change");
        Inventory small = new Inventory();
        small.load(new Item("A1", "Chips", 35), 5);
        CoinBank emptyHopper = new CoinBank();
        VendingMachine strict = new VendingMachine(small, emptyHopper);

        strict.insertCoin(Coin.FIFTY);
        expectFailure("selectItem(\"A1\") needing Rs.15 change", () -> strict.selectItem("A1"));
        List<Coin> back = strict.refund();
        System.out.println("  refunded: " + back + " -> state " + strict.stateName());
        System.out.println("  the sale was blocked BEFORE the item dropped, which is the point");

        System.out.println("\nDone.");
    }

    private static void expectFailure(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + " -> UNEXPECTEDLY SUCCEEDED");
        } catch (RuntimeException ex) {
            System.out.println("  " + label + " -> rejected: " + ex.getMessage());
        }
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
