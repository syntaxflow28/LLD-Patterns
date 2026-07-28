package com.lld.patterns.practical.specification;

import java.util.List;
import java.util.function.Predicate;

/**
 * SPECIFICATION — encapsulate a business rule in an object that can be evaluated, combined
 * (and/or/not), and reused. It's Strategy applied to boolean rules.
 *
 * When to use in LLD:
 *   - Filtering/search criteria, eligibility rules (discount, loan approval, fraud checks),
 *     validation, feature-flag targeting, matching engines.
 *
 * Why interviewers like it: it kills long compound `if` conditions and lets rules be composed at
 * runtime and unit-tested individually.
 */

interface Specification<T> {
    boolean isSatisfiedBy(T candidate);

    // Combinators: build complex rules from simple ones without new classes.
    default Specification<T> and(Specification<T> other) { return c -> this.isSatisfiedBy(c) && other.isSatisfiedBy(c); }
    default Specification<T> or(Specification<T> other)  { return c -> this.isSatisfiedBy(c) || other.isSatisfiedBy(c); }
    default Specification<T> not()                       { return c -> !this.isSatisfiedBy(c); }

    default Predicate<T> toPredicate() { return this::isSatisfiedBy; }   // plugs into Stream.filter
}

class Product {
    final String name; final String category; final double price; final boolean inStock;
    Product(String name, String category, double price, boolean inStock) {
        this.name = name; this.category = category; this.price = price; this.inStock = inStock;
    }
    @Override public String toString() { return name + " (" + category + ", $" + price + ")"; }
}

/** Each rule is a small, independently testable class. */
class InStockSpec implements Specification<Product> {
    public boolean isSatisfiedBy(Product p) { return p.inStock; }
}

class CategorySpec implements Specification<Product> {
    private final String category;
    CategorySpec(String category) { this.category = category; }
    public boolean isSatisfiedBy(Product p) { return p.category.equalsIgnoreCase(category); }
}

class PriceBelowSpec implements Specification<Product> {
    private final double limit;
    PriceBelowSpec(double limit) { this.limit = limit; }
    public boolean isSatisfiedBy(Product p) { return p.price < limit; }
}

public class SpecificationDemo {
    public static void main(String[] args) {
        List<Product> catalog = List.of(
                new Product("Laptop",   "electronics", 1200, true),
                new Product("Mouse",    "electronics",   25, true),
                new Product("Monitor",  "electronics",  300, false),
                new Product("Novel",    "books",         15, true));

        // Compose the rule at runtime instead of writing a nested if-statement.
        Specification<Product> affordableElectronics =
                new CategorySpec("electronics")
                        .and(new PriceBelowSpec(500))
                        .and(new InStockSpec());

        System.out.println("Affordable, in-stock electronics:");
        catalog.stream().filter(affordableElectronics.toPredicate())
                .forEach(p -> System.out.println("  " + p));

        Specification<Product> outOfStock = new InStockSpec().not();
        System.out.println("Out of stock:");
        catalog.stream().filter(outOfStock.toPredicate())
                .forEach(p -> System.out.println("  " + p));
    }
}
