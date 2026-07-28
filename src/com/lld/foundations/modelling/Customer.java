package com.lld.foundations.modelling;

/**
 * An entity, and one half of the "tell, don't ask" demonstration.
 *
 * <p>{@link #address()} exists so the demo can show the getter chain failing. In a design you would
 * defend, it would not exist at all - {@link #isBasedIn(String)} answers the only question anyone was
 * asking, and answers it without exposing that an address is stored, that it is nullable, or that the
 * country lives on it.
 *
 * <p><b>Every getter you delete is a coupling you delete.</b> The null check lives here, once, on the
 * object that knows the field can be null, instead of at every call site that happens to remember.
 */
public final class Customer {

    private final String name;
    private final Address address; // nullable: a customer can exist before we know where they are

    public Customer(String name, Address address) {
        this.name = name;
        this.address = address;
    }

    public String name() {
        return name;
    }

    /** The "ask" accessor. Present only so the demo can break it. */
    public Address address() {
        return address;
    }

    /** The "tell" alternative: intent in, answer out, no object graph exposed. */
    public boolean isBasedIn(String countryCode) {
        return address != null && address.country().equals(countryCode);
    }
}
