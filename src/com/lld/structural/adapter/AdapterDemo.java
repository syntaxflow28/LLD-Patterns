package com.lld.structural.adapter;

/*
 * ADAPTER — convert one interface into another that a client expects. Lets classes with
 * incompatible interfaces collaborate. Think of a power plug adapter.
 *
 * When to use in LLD:
 *   - Integrating a third-party/legacy SDK whose API doesn't match your domain interface, without
 *     rewriting either side.
 *
 * Here: our app expects a PaymentGateway, but a third-party library exposes a different API.
 */

/** The interface our application depends on (the "target"). */
interface PaymentGateway {
    boolean pay(String orderId, double amount);
}

/** Third-party class we cannot change (the "adaptee") — different method names/shape. */
class StripeApi {
    void makePayment(String currency, long amountInCents, String reference) {
        System.out.println("Stripe charged " + amountInCents + " " + currency + " for " + reference);
    }
}

/** The adapter: implements our interface, translates calls to the adaptee. */
class StripeAdapter implements PaymentGateway {
    private final StripeApi stripe;

    StripeAdapter(StripeApi stripe) { this.stripe = stripe; }

    public boolean pay(String orderId, double amount) {
        long cents = Math.round(amount * 100);       // translate the data shape
        stripe.makePayment("USD", cents, orderId);   // delegate to the adaptee
        return true;
    }
}

public class AdapterDemo {
    public static void main(String[] args) {
        // App code depends only on PaymentGateway; the Stripe specifics are hidden behind the adapter.
        PaymentGateway gateway = new StripeAdapter(new StripeApi());
        boolean ok = gateway.pay("ORDER-42", 19.99);
        System.out.println("Payment success? " + ok);
    }
}
