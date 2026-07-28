package com.lld.patterns.structural.facade;

/**
 * FACADE — provide a simple, unified interface to a complex subsystem. Clients call one method;
 * the facade coordinates the messy internals.
 *
 * When to use in LLD:
 *   - An operation touches many subsystems (inventory, payment, shipping, notification). A facade
 *     hides the orchestration behind a clean entry point like placeOrder().
 *
 * It reduces coupling: clients depend on the facade, not on every subsystem.
 */

class InventoryService {
    boolean reserve(String item, int qty) {
        System.out.println("Reserved " + qty + "x " + item);
        return true;
    }
}

class PaymentService {
    boolean charge(String account, double amount) {
        System.out.println("Charged $" + amount + " to " + account);
        return true;
    }
}

class ShippingService {
    String ship(String item, String address) {
        System.out.println("Shipping " + item + " to " + address);
        return "TRACK-" + item.hashCode();
    }
}

class NotificationService {
    void notifyUser(String msg) { System.out.println("Notify: " + msg); }
}

/** The facade orchestrates the four subsystems behind one call. */
class OrderFacade {
    private final InventoryService inventory = new InventoryService();
    private final PaymentService payment = new PaymentService();
    private final ShippingService shipping = new ShippingService();
    private final NotificationService notifier = new NotificationService();

    String placeOrder(String item, int qty, String account, double amount, String address) {
        if (!inventory.reserve(item, qty)) throw new IllegalStateException("Out of stock");
        if (!payment.charge(account, amount)) throw new IllegalStateException("Payment failed");
        String tracking = shipping.ship(item, address);
        notifier.notifyUser("Order placed. Tracking: " + tracking);
        return tracking;
    }
}

public class FacadeDemo {
    public static void main(String[] args) {
        // Client does ONE call; all subsystem coordination is hidden.
        OrderFacade store = new OrderFacade();
        String tracking = store.placeOrder("Keyboard", 1, "acct-7", 49.99, "221B Baker St");
        System.out.println("Done. Tracking = " + tracking);
    }
}
