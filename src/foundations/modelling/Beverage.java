package foundations.modelling;

/**
 * The contract that lets add-ons compose instead of multiply.
 *
 * <p>With inheritance, {@code n} independent options need 2^n subclasses -
 * {@code CoffeeWithMilkAndSugarAndCreamAndSyrup} is a real class name in real codebases. With
 * composition, each option is one class that wraps a {@code Beverage} and is itself a
 * {@code Beverage}, so combinations are built at runtime instead of declared at compile time.
 *
 * <p>That is the Decorator pattern, but notice the route: nobody needed to know its name. The
 * question "what changes independently of what?" produced it. <b>The pattern is the destination, not
 * the route</b> - and being able to derive it is worth more in an interview than being able to
 * recite it.
 */
public interface Beverage {

    String description();

    Money cost();
}
