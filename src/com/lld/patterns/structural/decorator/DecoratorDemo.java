package com.lld.patterns.structural.decorator;

/**
 * DECORATOR — attach responsibilities to an object dynamically by wrapping it. A flexible
 * alternative to subclassing for extending behavior.
 *
 * When to use in LLD:
 *   - Layered, combinable features: coffee add-ons, I/O streams (BufferedInputStream wraps
 *     FileInputStream), pricing modifiers, request/response middleware.
 *
 * Key idea: both the concrete object and the decorators share one interface, so wrappers nest.
 */

interface Coffee {
    String description();
    double cost();
}

class SimpleCoffee implements Coffee {
    public String description() { return "Coffee"; }
    public double cost() { return 2.00; }
}

/** Base decorator: holds a wrapped Coffee and forwards by default. */
abstract class CoffeeDecorator implements Coffee {
    protected final Coffee inner;
    CoffeeDecorator(Coffee inner) { this.inner = inner; }
}

class MilkDecorator extends CoffeeDecorator {
    MilkDecorator(Coffee inner) { super(inner); }
    public String description() { return inner.description() + " + Milk"; }
    public double cost() { return inner.cost() + 0.50; }
}

class SugarDecorator extends CoffeeDecorator {
    SugarDecorator(Coffee inner) { super(inner); }
    public String description() { return inner.description() + " + Sugar"; }
    public double cost() { return inner.cost() + 0.25; }
}

class WhipDecorator extends CoffeeDecorator {
    WhipDecorator(Coffee inner) { super(inner); }
    public String description() { return inner.description() + " + Whip"; }
    public double cost() { return inner.cost() + 0.75; }
}

public class DecoratorDemo {
    public static void main(String[] args) {
        // Compose behavior by nesting wrappers — no explosion of subclasses.
        Coffee order = new WhipDecorator(new MilkDecorator(new SugarDecorator(new SimpleCoffee())));
        System.out.println(order.description() + " = $" + order.cost());
    }
}
