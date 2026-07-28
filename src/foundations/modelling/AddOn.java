package foundations.modelling;

import java.util.Objects;

/**
 * One optional extra, wrapping any {@link Beverage} and being one itself.
 *
 * <p>This single class replaces the entire {@code ...WithMilkAndSugar} subclass tree. It is a
 * {@code Beverage} (so callers never learn a new type) and it <em>holds</em> a {@code Beverage} (so
 * extras stack to any depth). Being both is what makes the recursion work, and it is the shape worth
 * recognising: the same "implements X, holds an X" structure is Decorator, Composite and Proxy.
 *
 * <p>The wrapped reference is <b>aggregation, not composition</b> in the ownership sense - the add-on
 * does not own the drink it decorates, and the same base could in principle be wrapped by something
 * else. Getting that word right is something interviewers listen for.
 */
public final class AddOn implements Beverage {

    private final Beverage wrapped;
    private final String name;
    private final Money surcharge;

    public AddOn(Beverage wrapped, String name, Money surcharge) {
        this.wrapped = Objects.requireNonNull(wrapped, "wrapped");
        this.name = Objects.requireNonNull(name, "name");
        this.surcharge = Objects.requireNonNull(surcharge, "surcharge");
    }

    @Override
    public String description() {
        return wrapped.description() + " + " + name;
    }

    @Override
    public Money cost() {
        // Money.plus refuses mixed currencies, so a mis-priced add-on cannot silently corrupt a total.
        return wrapped.cost().plus(surcharge);
    }
}
