package com.lld.patterns.structural.bridge;

/*
 * BRIDGE — decouple an abstraction from its implementation so the two can vary independently.
 *
 * The signal you need it: a class hierarchy is exploding along TWO dimensions.
 *   Shape x Renderer  ->  VectorCircle, RasterCircle, VectorSquare, RasterSquare... (M x N classes)
 * Bridge turns that into M + N: Shape holds a Renderer reference (the "bridge").
 *
 * When to use in LLD:
 *   - Notification (Alert type x Channel), Remote controls (Device x UI), Reports (Format x Source),
 *     Persistence (Entity x Storage engine).
 *
 * Bridge vs Adapter: Adapter retro-fits incompatible code; Bridge is designed up front to keep two
 * dimensions independent.
 */

/** Implementor side — the "how it is drawn" dimension. */
interface Renderer {
    void renderCircle(double radius);
    void renderSquare(double side);
}

class VectorRenderer implements Renderer {
    public void renderCircle(double r) { System.out.println("Vector: circle of radius " + r); }
    public void renderSquare(double s) { System.out.println("Vector: square of side " + s); }
}

class RasterRenderer implements Renderer {
    public void renderCircle(double r) { System.out.println("Raster: pixels for circle radius " + r); }
    public void renderSquare(double s) { System.out.println("Raster: pixels for square side " + s); }
}

/** Abstraction side — the "what it is" dimension. Holds a Renderer instead of subclassing per renderer. */
abstract class Shape {
    protected final Renderer renderer;          // <-- the bridge
    Shape(Renderer renderer) { this.renderer = renderer; }
    abstract void draw();
    abstract void resize(double factor);
}

class Circle extends Shape {
    private double radius;
    Circle(Renderer renderer, double radius) { super(renderer); this.radius = radius; }
    void draw() { renderer.renderCircle(radius); }
    void resize(double factor) { radius *= factor; }
}

class Square extends Shape {
    private double side;
    Square(Renderer renderer, double side) { super(renderer); this.side = side; }
    void draw() { renderer.renderSquare(side); }
    void resize(double factor) { side *= factor; }
}

public class BridgeDemo {
    public static void main(String[] args) {
        // Any shape can pair with any renderer — no combinatorial subclasses.
        Shape a = new Circle(new VectorRenderer(), 5);
        Shape b = new Circle(new RasterRenderer(), 5);
        Shape c = new Square(new VectorRenderer(), 3);

        a.draw();
        b.draw();
        c.draw();

        a.resize(2);   // abstraction-side logic is shared across all renderers
        a.draw();
    }
}
