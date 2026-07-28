package patterns.behavioral.visitor;

import java.util.List;

/**
 * VISITOR — separate an algorithm from the object structure it operates on. Add new *operations*
 * over a stable class hierarchy without modifying those classes.
 *
 * Trade-off to state in an interview:
 *   + Easy to add new OPERATIONS (new visitor class).
 *   - Hard to add new ELEMENT types (every visitor must change).
 *   Use it only when the element hierarchy is stable but operations keep growing.
 *
 * When to use in LLD:
 *   - AST processing (compiler passes), exporting a document tree to PDF/HTML/Markdown, computing
 *     tax/discount/shipping over a heterogeneous cart, file-system reports.
 *
 * Mechanism: "double dispatch" — element.accept(visitor) then visitor.visit(this).
 */

interface CartVisitor {
    double visit(Book book);
    double visit(Electronics item);
    double visit(Groceries item);
}

interface CartItem {
    double accept(CartVisitor visitor);   // double dispatch entry point
}

class Book implements CartItem {
    final double price; final String isbn;
    Book(double price, String isbn) { this.price = price; this.isbn = isbn; }
    public double accept(CartVisitor v) { return v.visit(this); }
}

class Electronics implements CartItem {
    final double price; final String sku;
    Electronics(double price, String sku) { this.price = price; this.sku = sku; }
    public double accept(CartVisitor v) { return v.visit(this); }
}

class Groceries implements CartItem {
    final double price; final double weightKg;
    Groceries(double price, double weightKg) { this.price = price; this.weightKg = weightKg; }
    public double accept(CartVisitor v) { return v.visit(this); }
}

/** Operation #1: tax rules differ per item type. */
class TaxVisitor implements CartVisitor {
    public double visit(Book b)        { return b.price * 0.00; }   // books exempt
    public double visit(Electronics e) { return e.price * 0.18; }
    public double visit(Groceries g)   { return g.price * 0.05; }
}

/** Operation #2: shipping cost — added WITHOUT touching any CartItem class. */
class ShippingVisitor implements CartVisitor {
    public double visit(Book b)        { return 2.0; }
    public double visit(Electronics e) { return 8.0; }
    public double visit(Groceries g)   { return 1.5 * g.weightKg; }
}

public class VisitorDemo {
    public static void main(String[] args) {
        List<CartItem> cart = List.of(
                new Book(20, "978-1"),
                new Electronics(500, "TV-55"),
                new Groceries(30, 4.0));

        System.out.printf("Total tax      = %.2f%n", total(cart, new TaxVisitor()));
        System.out.printf("Total shipping = %.2f%n", total(cart, new ShippingVisitor()));
    }

    private static double total(List<CartItem> cart, CartVisitor visitor) {
        double sum = 0;
        for (CartItem item : cart) sum += item.accept(visitor);
        return sum;
    }
}
