package com.lld.patterns.creational.builder;

/**
 * BUILDER — construct a complex object step by step. Best when a class has many fields, several
 * optional, and you want readable, immutable construction without a telescoping constructor.
 *
 * When to use in LLD:
 *   - Objects like HttpRequest, Pizza, DatabaseConfig, User with lots of optional attributes.
 *
 * Benefits: no 6-argument constructors, immutable result, self-documenting call site, and you can
 * validate in build().
 */

class Pizza {
    // All fields final => immutable once built.
    private final String size;          // required
    private final boolean cheese;       // optional
    private final boolean pepperoni;    // optional
    private final boolean mushrooms;    // optional

    private Pizza(Builder b) {           // private ctor: only the Builder can create a Pizza
        this.size = b.size;
        this.cheese = b.cheese;
        this.pepperoni = b.pepperoni;
        this.mushrooms = b.mushrooms;
    }

    @Override public String toString() {
        return "Pizza{size=" + size + ", cheese=" + cheese
                + ", pepperoni=" + pepperoni + ", mushrooms=" + mushrooms + "}";
    }

    static Builder builder(String size) { return new Builder(size); }

    /** The fluent builder. Each setter returns 'this' so calls chain. */
    static class Builder {
        private final String size;       // required arg lives in the builder ctor
        private boolean cheese;
        private boolean pepperoni;
        private boolean mushrooms;

        Builder(String size) { this.size = size; }

        Builder cheese()    { this.cheese = true;    return this; }
        Builder pepperoni() { this.pepperoni = true; return this; }
        Builder mushrooms() { this.mushrooms = true; return this; }

        Pizza build() {
            if (size == null || size.isBlank())
                throw new IllegalStateException("size is required"); // validate before creating
            return new Pizza(this);
        }
    }
}

public class BuilderDemo {
    public static void main(String[] args) {
        Pizza a = Pizza.builder("large").cheese().pepperoni().build();
        Pizza b = Pizza.builder("small").cheese().mushrooms().build();

        System.out.println(a);
        System.out.println(b);
    }
}
