package foundations.modelling;

/** A value object. Two addresses with the same fields are the same address - no identity needed. */
public record Address(String street, String city, String country) {
}
