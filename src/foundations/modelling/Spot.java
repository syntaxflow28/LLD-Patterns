package foundations.modelling;

/** A part that cannot exist without its {@link Floor} - the classic composition relationship. */
public final class Spot {

    private final int number;
    private boolean occupied;

    Spot(int number) {
        this.number = number;
    }

    public int number() {
        return number;
    }

    public boolean isFree() {
        return !occupied;
    }

    void occupy() {
        occupied = true;
    }

    @Override
    public String toString() {
        return "spot-" + number + (occupied ? "(taken)" : "(free)");
    }
}
