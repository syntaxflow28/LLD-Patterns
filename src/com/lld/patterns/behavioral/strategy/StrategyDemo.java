package com.lld.patterns.behavioral.strategy;

/**
 * STRATEGY — define a family of interchangeable algorithms, encapsulate each, and make them
 * swappable at runtime. The client picks which strategy to use.
 *
 * This is the #1 LLD interview pattern. It's the go-to way to honor Open/Closed: new behavior =
 * new strategy class, no edits to existing code.
 *
 * When to use in LLD:
 *   - Payment methods, pricing/discount rules, sorting, route finding, cache eviction, split
 *     types in Splitwise — anywhere "how" varies independently of "when".
 */

interface PaymentStrategy {
    void pay(double amount);
}

class CreditCardPayment implements PaymentStrategy {
    private final String card;
    CreditCardPayment(String card) { this.card = card; }
    public void pay(double amount) { System.out.println("Paid $" + amount + " via credit card " + card); }
}

class UpiPayment implements PaymentStrategy {
    private final String vpa;
    UpiPayment(String vpa) { this.vpa = vpa; }
    public void pay(double amount) { System.out.println("Paid $" + amount + " via UPI " + vpa); }
}

class WalletPayment implements PaymentStrategy {
    public void pay(double amount) { System.out.println("Paid $" + amount + " via wallet"); }
}

/** Context holds a strategy and delegates to it; it doesn't know the concrete algorithm. */
class CheckoutService {
    private PaymentStrategy strategy;

    void setPaymentStrategy(PaymentStrategy strategy) { this.strategy = strategy; } // swap at runtime

    void checkout(double amount) {
        if (strategy == null) throw new IllegalStateException("Select a payment method first");
        strategy.pay(amount);
    }
}

public class StrategyDemo {
    public static void main(String[] args) {
        CheckoutService checkout = new CheckoutService();

        checkout.setPaymentStrategy(new CreditCardPayment("****1234"));
        checkout.checkout(120.00);

        checkout.setPaymentStrategy(new UpiPayment("alice@bank"));   // swapped, no code change
        checkout.checkout(75.50);

        checkout.setPaymentStrategy(new WalletPayment());
        checkout.checkout(9.99);
    }
}
