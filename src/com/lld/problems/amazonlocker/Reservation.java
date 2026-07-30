package com.lld.problems.amazonlocker;

import java.time.Instant;
import java.util.Objects;

/**
 * One parcel's claim on one locker door, from allocation to pickup.
 *
 * <p><b>Immutable, and transitions return a new instance.</b> That is not decoration — it is what
 * makes {@link Locker}'s compare-and-set state machine possible. Every transition is "swap the
 * reservation object I read for this new one, but only if nobody swapped it first". A mutable
 * reservation would need a lock per locker to get the same guarantee.
 *
 * <p><b>{@code deadline} means different things in different states, on purpose.</b> While
 * {@link ReservationStatus#AWAITING_DROP_OFF} it is the courier's window (hold a door too long and
 * you starve the station); while {@link ReservationStatus#AWAITING_PICKUP} it is the customer's
 * retention window (three days, then return to sender). One field, because the question asked at
 * both points is identical: <em>has this expired?</em>
 *
 * <p>Note that {@code accessCode} is null until drop-off. The code is minted when the parcel is
 * physically inside, not when the door is booked — otherwise a code exists for a locker containing
 * nothing, and a courier no-show leaves a live secret in the customer's inbox.
 */
public record Reservation(
        String id,
        Parcel parcel,
        ReservationStatus status,
        AccessCode accessCode,
        Instant createdAt,
        Instant deadline) {

    public Reservation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(parcel, "parcel");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(deadline, "deadline");
    }

    static Reservation awaitingDropOff(String id, Parcel parcel, Instant now, Instant deadline) {
        return new Reservation(id, parcel, ReservationStatus.AWAITING_DROP_OFF, null, now, deadline);
    }

    Reservation droppedOff(AccessCode code, Instant pickupDeadline) {
        return transitionTo(ReservationStatus.AWAITING_PICKUP, code, pickupDeadline);
    }

    Reservation pickedUp() {
        return transitionTo(ReservationStatus.PICKED_UP, accessCode, deadline);
    }

    Reservation expired() {
        // The code dies with the reservation; an expired parcel must not be openable by an old SMS.
        return transitionTo(ReservationStatus.EXPIRED, null, deadline);
    }

    /** The guard lives here, so no caller can construct an illegal transition by hand. */
    private Reservation transitionTo(ReservationStatus next, AccessCode code, Instant nextDeadline) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal transition " + status + " -> " + next);
        }
        return new Reservation(id, parcel, next, code, createdAt, nextDeadline);
    }

    /** Deadlines are absolute instants, never countdowns — a paused JVM must not extend a window. */
    boolean hasExpiredAt(Instant now) {
        return !now.isBefore(deadline);
    }
}
