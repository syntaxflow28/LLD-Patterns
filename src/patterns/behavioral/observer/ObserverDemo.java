package patterns.behavioral.observer;

import java.util.ArrayList;
import java.util.List;

/**
 * OBSERVER — define a one-to-many dependency so that when one object (subject) changes state, all
 * its dependents (observers) are notified automatically. Publish/subscribe.
 *
 * When to use in LLD:
 *   - Notifications, event systems, stock tickers, "someone updated X, refresh the UI",
 *     order-status broadcasts to email/SMS/analytics.
 *
 * Subject knows only the Observer interface -> loose coupling; add subscribers without changing it.
 */

interface Observer {
    void update(String stock, double price);
}

interface Subject {
    void subscribe(Observer o);
    void unsubscribe(Observer o);
    void notifyObservers();
}

class StockTicker implements Subject {
    private final List<Observer> observers = new ArrayList<>();
    private String stock;
    private double price;

    public void subscribe(Observer o)   { observers.add(o); }
    public void unsubscribe(Observer o) { observers.remove(o); }

    public void notifyObservers() {
        for (Observer o : observers) o.update(stock, price); // broadcast to all subscribers
    }

    void setPrice(String stock, double price) {  // state change triggers a notification
        this.stock = stock;
        this.price = price;
        notifyObservers();
    }
}

class EmailAlert implements Observer {
    public void update(String stock, double price) {
        System.out.println("Email: " + stock + " is now $" + price);
    }
}

class MobilePushAlert implements Observer {
    public void update(String stock, double price) {
        System.out.println("Push: " + stock + " -> $" + price);
    }
}

public class ObserverDemo {
    public static void main(String[] args) {
        StockTicker ticker = new StockTicker();
        Observer email = new EmailAlert();
        Observer push = new MobilePushAlert();

        ticker.subscribe(email);
        ticker.subscribe(push);

        ticker.setPrice("ACME", 101.25);   // both observers notified

        ticker.unsubscribe(email);
        System.out.println("--- email unsubscribed ---");
        ticker.setPrice("ACME", 99.80);    // only push notified
    }
}
