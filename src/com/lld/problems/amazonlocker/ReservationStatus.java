package com.lld.problems.amazonlocker;

import java.util.EnumSet;
import java.util.Set;

/**
 * The reservation lifecycle, with the legal transitions written down once.
 *
 * <pre>
 *   AWAITING_DROP_OFF --courier drops--> AWAITING_PICKUP --correct code--> PICKED_UP
 *          |                                    |
 *          +--------- window elapsed -----------+----------------------> EXPIRED
 * </pre>
 *
 * <p><b>Why an enum and not State classes here.</b> The vending machine gets State classes because
 * every operation behaves differently per state and the machine drives itself through them. Here
 * there are four states, two happy transitions, and the behaviour differences are a single guard at
 * the top of two methods. An enum plus {@link #canTransitionTo} is the proportionate answer; a
 * {@code ReservationState} interface with four implementations would be pattern tax. Being able to
 * say <em>why you did not use State</em> scores better than using it.
 */
public enum ReservationStatus {

    /** Locker is held for a courier who has not arrived yet. */
    AWAITING_DROP_OFF,

    /** Parcel is inside; the customer has a code and a retention window. */
    AWAITING_PICKUP,

    /** Terminal: customer opened the locker. */
    PICKED_UP,

    /** Terminal: a window elapsed. The parcel goes back to the carrier. */
    EXPIRED;

    private static final Set<ReservationStatus> TERMINAL = EnumSet.of(PICKED_UP, EXPIRED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(ReservationStatus next) {
        return switch (this) {
            case AWAITING_DROP_OFF -> next == AWAITING_PICKUP || next == EXPIRED;
            case AWAITING_PICKUP -> next == PICKED_UP || next == EXPIRED;
            case PICKED_UP, EXPIRED -> false;
        };
    }
}
