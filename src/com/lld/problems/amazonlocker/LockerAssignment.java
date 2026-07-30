package com.lld.problems.amazonlocker;

import java.time.Instant;

/**
 * What a courier's handheld gets back when a door is booked.
 *
 * <p>A DTO, not the {@link Reservation} itself: the courier app has no business seeing the access
 * code field, and handing out the internal aggregate is how internal state ends up serialised into
 * a public API and frozen there forever.
 *
 * @param reservationId  quote this at drop-off
 * @param locationId     which station to drive to
 * @param lockerId       which door will open
 * @param lockerSize     so the app can warn "that parcel will not fit" before the van leaves
 * @param dropOffDeadline after this, the door is released to someone else
 */
public record LockerAssignment(
        String reservationId,
        String locationId,
        String lockerId,
        LockerSize lockerSize,
        Instant dropOffDeadline) {
}
