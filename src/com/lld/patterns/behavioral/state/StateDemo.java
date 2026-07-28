package com.lld.patterns.behavioral.state;

/**
 * STATE — let an object alter its behavior when its internal state changes; it appears to change
 * class. Replaces sprawling if/else or switch on a "status" field with polymorphic state objects.
 *
 * When to use in LLD:
 *   - Vending machine, order lifecycle, TCP connection, document workflow, elevator — anything
 *     with a clear state machine where each state handles events differently and controls
 *     transitions.
 *
 * Strategy vs State: Strategy is chosen by the client; State transitions itself based on events.
 */

interface VendingState {
    void insertCoin(VendingMachine m);
    void selectProduct(VendingMachine m);
    void dispense(VendingMachine m);
}

class VendingMachine {
    // Pre-created states (could be created lazily too).
    final VendingState idle = new IdleState();
    final VendingState hasCoin = new HasCoinState();
    final VendingState dispensing = new DispensingState();

    private VendingState current = idle;

    void setState(VendingState s) { this.current = s; }

    // Public API delegates to the current state — no if/else on status anywhere.
    void insertCoin()    { current.insertCoin(this); }
    void selectProduct() { current.selectProduct(this); }
    void dispense()      { current.dispense(this); }
}

class IdleState implements VendingState {
    public void insertCoin(VendingMachine m)    { System.out.println("Coin accepted"); m.setState(m.hasCoin); }
    public void selectProduct(VendingMachine m) { System.out.println("Insert a coin first"); }
    public void dispense(VendingMachine m)      { System.out.println("Insert a coin first"); }
}

class HasCoinState implements VendingState {
    public void insertCoin(VendingMachine m)    { System.out.println("Coin already inserted"); }
    public void selectProduct(VendingMachine m) { System.out.println("Product selected"); m.setState(m.dispensing); }
    public void dispense(VendingMachine m)      { System.out.println("Select a product first"); }
}

class DispensingState implements VendingState {
    public void insertCoin(VendingMachine m)    { System.out.println("Please wait, dispensing"); }
    public void selectProduct(VendingMachine m) { System.out.println("Already dispensing"); }
    public void dispense(VendingMachine m)      { System.out.println("Product dispensed. Thank you!"); m.setState(m.idle); }
}

public class StateDemo {
    public static void main(String[] args) {
        VendingMachine m = new VendingMachine();

        m.selectProduct();  // rejected in Idle
        m.insertCoin();     // Idle -> HasCoin
        m.selectProduct();  // HasCoin -> Dispensing
        m.dispense();       // Dispensing -> Idle
    }
}
