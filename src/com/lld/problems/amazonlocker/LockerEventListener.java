package com.lld.problems.amazonlocker;

/**
 * OBSERVER — things that must happen when a locker changes hands, that the locker must not know
 * about.
 *
 * <p>The requirement "text the customer their pickup code" is the reason this exists. Wiring an
 * {@code SmsClient} field into {@link LockerService} means the next requirement — email as well,
 * then app push, then an audit trail, then a metrics counter — edits the service every time. As
 * listeners, each is a class nobody else has to know about.
 *
 * <p><b>The security rule that comes with the pattern.</b> {@link #onParcelDroppedOff} is the only
 * place in the system where a plaintext code exists after generation. A listener that delivers it to
 * the customer needs it; a listener that writes a log line must never print it. That split is
 * exactly why the two implementations below take the same event and treat it differently.
 */
public interface LockerEventListener {

    default void onLockerReserved(LockerLocation location, Locker locker, Reservation reservation) {
    }

    /** @param plaintextCode deliver it or discard it — never log it, never store it */
    default void onParcelDroppedOff(Locker locker, Reservation reservation, String plaintextCode) {
    }

    default void onParcelPickedUp(Locker locker, Reservation reservation) {
    }

    default void onReservationExpired(Locker locker, Reservation reservation, String reason) {
    }

    default void onAccessDenied(Locker locker, int failedAttempts, boolean lockedOut) {
    }

    /**
     * Operational trail. Note what is missing: the code. This class receives it and drops it on the
     * floor, which is the behaviour a security reviewer checks for first.
     */
    final class AuditLog implements LockerEventListener {

        @Override
        public void onLockerReserved(LockerLocation location, Locker locker, Reservation reservation) {
            log("RESERVED  door=" + locker.id() + " station=" + location.id()
                    + " parcel=" + reservation.parcel().trackingId());
        }

        @Override
        public void onParcelDroppedOff(Locker locker, Reservation reservation, String plaintextCode) {
            log("DROPPED   door=" + locker.id() + " parcel=" + reservation.parcel().trackingId()
                    + " code=<redacted> pickupBy=" + reservation.deadline());
        }

        @Override
        public void onParcelPickedUp(Locker locker, Reservation reservation) {
            log("PICKEDUP  door=" + locker.id() + " parcel=" + reservation.parcel().trackingId());
        }

        @Override
        public void onReservationExpired(Locker locker, Reservation reservation, String reason) {
            log("EXPIRED   door=" + locker.id() + " parcel=" + reservation.parcel().trackingId()
                    + " reason=" + reason);
        }

        @Override
        public void onAccessDenied(Locker locker, int failedAttempts, boolean lockedOut) {
            log("DENIED    door=" + locker.id() + " attempts=" + failedAttempts
                    + (lockedOut ? " LOCKED_OUT" : ""));
        }

        private void log(String message) {
            System.out.println("      [audit] " + message);
        }
    }

    /** Stands in for SMS/email/push. The one listener that legitimately handles the code. */
    final class CustomerNotifier implements LockerEventListener {

        @Override
        public void onParcelDroppedOff(Locker locker, Reservation reservation, String plaintextCode) {
            System.out.println("      [notify] to " + mask(reservation.parcel().recipientHandle())
                    + ": parcel " + reservation.parcel().trackingId() + " is in door " + locker.id()
                    + ", code " + plaintextCode + ", collect by " + reservation.deadline());
        }

        @Override
        public void onReservationExpired(Locker locker, Reservation reservation, String reason) {
            System.out.println("      [notify] to " + mask(reservation.parcel().recipientHandle())
                    + ": parcel " + reservation.parcel().trackingId() + " was returned (" + reason + ")");
        }

        /** Contact details are personal data; a demo trace is still a log. */
        private String mask(String handle) {
            int keep = Math.min(3, handle.length());
            return "*".repeat(handle.length() - keep) + handle.substring(handle.length() - keep);
        }
    }
}
