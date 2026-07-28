package foundations.modelling;

/**
 * The base type in the canonical Liskov failure.
 *
 * <p>This class makes a promise that is not in any signature: <b>width and height move
 * independently</b>. Every caller that sets one and reads the other relies on it, and the compiler
 * cannot see it. Inheritance inherits the promises, not just the fields - which is exactly why
 * {@link Square} cannot extend this class no matter how true "a square is a rectangle" is in
 * geometry.
 */
public class Rectangle {

    private int width;
    private int height;

    public void setWidth(int width) {
        this.width = width;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int area() {
        return width * height;
    }
}
