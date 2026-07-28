package foundations.modelling;

/** The base drink. One class, no variants - every variant is composed at runtime. */
public final class Coffee implements Beverage {

    private final Money base;

    public Coffee(Money base) {
        this.base = base;
    }

    @Override
    public String description() {
        return "coffee";
    }

    @Override
    public Money cost() {
        return base;
    }
}
