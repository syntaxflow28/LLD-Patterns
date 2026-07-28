package foundations.modelling;

/**
 * An entity that demonstrates both "tell, don't ask" and the boolean-flag trap.
 *
 * <p><b>On the flags.</b> {@code isSubmitted}, {@code isApproved} and {@code isPaid} encode 2^3 = 8
 * states, of which only 4 are meaningful. The other 4 - paid but not approved, approved but not
 * submitted - are constructible, and every method that reads them has to decide what they mean. An
 * {@link Status} enum encodes exactly the states that exist, and the illegal combinations stop being
 * representable rather than being checked for.
 *
 * <p><b>n booleans on a class is a question about which combinations are legal.</b> If the answer is
 * not "all of them", they wanted to be an enum or a sealed hierarchy.
 */
public final class Order {

    /** The four states that actually exist, replacing eight combinations of three booleans. */
    public enum Status {
        DRAFT, SUBMITTED, APPROVED, PAID
    }

    private final Customer customer;
    private final Money total;
    private Status status = Status.DRAFT;

    public Order(Customer customer, Money total) {
        this.customer = customer;
        this.total = total;
    }

    /** The "ask" accessor, present only so the demo can break the chain that uses it. */
    public Customer customer() {
        return customer;
    }

    /**
     * The "tell" alternative. One dot instead of four, and the caller is coupled to the concept of
     * "domestic" rather than to the existence of Customer, Address and a country field.
     */
    public boolean isDomestic() {
        return customer.isBasedIn("IN");
    }

    public Status status() {
        return status;
    }

    public Money total() {
        return total;
    }

    /** Legal transitions live here, so an order cannot be paid before it is approved. */
    public void advanceTo(Status next) {
        boolean legal = switch (status) {
            case DRAFT -> next == Status.SUBMITTED;
            case SUBMITTED -> next == Status.APPROVED;
            case APPROVED -> next == Status.PAID;
            case PAID -> false;
        };
        if (!legal) {
            throw new IllegalStateException("cannot go from " + status + " to " + next);
        }
        status = next;
    }
}
