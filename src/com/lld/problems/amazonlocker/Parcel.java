package com.lld.problems.amazonlocker;

import java.util.Objects;

/**
 * The thing being delivered.
 *
 * <p><b>Why {@code Parcel} and not {@code Package}.</b> {@code java.lang.Package} exists and is
 * auto-imported into every compilation unit, so a class called {@code Package} shadows it and makes
 * every reflection call in the file read strangely. Small thing, but naming collisions with
 * {@code java.lang} are a real review comment.
 *
 * <p>A record because it is a value: two parcels with the same tracking id <em>are</em> the same
 * parcel, and nothing about it changes while it sits in a locker. The locker's state changes; the
 * parcel's does not.
 *
 * @param trackingId      carrier-scoped identity
 * @param orderId         links back to the order aggregate, which lives in another bounded context
 * @param size            the grade this parcel was boxed into
 * @param recipientHandle phone or email — how the pickup code gets delivered
 */
public record Parcel(String trackingId, String orderId, LockerSize size, String recipientHandle) {

    public Parcel {
        Objects.requireNonNull(trackingId, "trackingId");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(recipientHandle, "recipientHandle");
        if (trackingId.isBlank()) {
            throw new IllegalArgumentException("trackingId must not be blank");
        }
    }

    @Override
    public String toString() {
        return trackingId + " [" + size + "]";
    }
}
