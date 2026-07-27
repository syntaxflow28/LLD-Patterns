package com.lld.problems.vendingmachine;

/**
 * The motor is turning. Deliberately a state of its own rather than a flag.
 *
 * <p>Its value is what it <em>forbids</em>: you cannot insert coins mid-dispense (they would be
 * counted against a sale that is already priced) and you cannot change your selection. Both would
 * be real bugs in a single-state design with an {@code if (dispensing)} guard sprinkled around.
 *
 * <p>By this point every precondition has already been checked in HAS_MONEY, so this state cannot
 * fail for business reasons — only for hardware ones.
 */
public final class DispensingState implements VendingState {

    public static final VendingState INSTANCE = new DispensingState();

    private DispensingState() {
    }

    @Override
    public String name() {
        return "DISPENSING";
    }

    @Override
    public VendingMachine.Purchase dispense(VendingMachine machine) {
        return machine.completeSale();
    }
}
