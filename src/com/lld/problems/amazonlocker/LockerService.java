package com.lld.problems.amazonlocker;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * FACADE over the whole locker network. Four operations: {@link #reserve}, {@link #dropOff},
 * {@link #pickUp}, {@link #reclaimExpired}.
 *
 * <p>Three actors, three entry points, and that is the shape of the problem: the <b>carrier</b>
 * books a door, the <b>courier</b> fills it, the <b>customer</b> empties it. A design that only
 * models "customer collects parcel" has missed two thirds of it.
 *
 * <p><b>Why the two windows are the interesting part.</b> Every door is a scarce, physical resource
 * that a human can forget about. Without a drop-off deadline, a cancelled delivery holds a door
 * forever; without a retention deadline, one unclaimed parcel bricks a door permanently. Both are
 * absolute {@link Instant}s and both are enforced by the same sweep, which is why
 * {@link Reservation} has one {@code deadline} field rather than two.
 *
 * <p><b>Why {@link Clock} is injected.</b> A three-day retention window you can only test by waiting
 * three days is a window that never gets tested. The demo advances a fake clock instead.
 *
 * <p><b>Why this is not a Singleton.</b> A network has many stations and a test wants a throwaway
 * one. Construct it once in the composition root and inject it; uniqueness is a deployment concern,
 * not something the class should enforce with a private constructor.
 */
public class LockerService {

    private final String network;
    private final Clock clock;
    private final Duration dropOffWindow;
    private final Duration retentionWindow;
    private final int maxFailedAttempts;

    /** volatile: both are swappable at runtime (a station rolls out best-fit, codes go alphanumeric). */
    private volatile LockerAllocationStrategy allocationStrategy;
    private volatile AccessCodePolicy accessCodePolicy;

    private final Map<String, LockerLocation> locations;
    private final Map<String, Locker> lockerById;

    /** Drop-off quotes a reservation id, not a door — the index has to exist somewhere. */
    private final Map<String, Locker> lockerByReservationId = new ConcurrentHashMap<>();

    private final List<LockerEventListener> listeners = new CopyOnWriteArrayList<>();

    private LockerService(Builder builder) {
        this.network = builder.network;
        this.clock = builder.clock;
        this.dropOffWindow = builder.dropOffWindow;
        this.retentionWindow = builder.retentionWindow;
        this.maxFailedAttempts = builder.maxFailedAttempts;
        this.allocationStrategy = builder.allocationStrategy;
        this.accessCodePolicy = builder.accessCodePolicy;
        this.locations = Map.copyOf(builder.locations);

        Map<String, Locker> lockers = new LinkedHashMap<>();
        for (LockerLocation location : locations.values()) {
            for (Locker locker : location.lockers()) {
                lockers.put(locker.id(), locker);
            }
        }
        this.lockerById = Map.copyOf(lockers);
    }

    // ---------------------------------------------------------------- carrier: book a door

    /**
     * Holds a door at a named station for a courier who is on the way.
     *
     * @throws NoLockerAvailableException if nothing at that station fits the parcel right now
     */
    public LockerAssignment reserve(Parcel parcel, String locationId) {
        Objects.requireNonNull(parcel, "parcel");
        LockerLocation location = locations.get(locationId);
        if (location == null) {
            throw new IllegalArgumentException("Unknown station: " + locationId);
        }

        Instant now = clock.instant();
        Reservation reservation = Reservation.awaitingDropOff(
                "RSV-" + UUID.randomUUID().toString().substring(0, 8),
                parcel,
                now,
                now.plus(dropOffWindow));

        Locker locker = allocationStrategy.allocate(location, reservation)
                .orElseThrow(() -> new NoLockerAvailableException(
                        "No free " + parcel.size() + "-capable door at " + location));

        lockerByReservationId.put(reservation.id(), locker);
        publish(listener -> listener.onLockerReserved(location, locker, reservation));

        return new LockerAssignment(
                reservation.id(), location.id(), locker.id(), locker.size(), reservation.deadline());
    }

    /**
     * The "find me a locker near work" path: rank stations by distance, try each in turn.
     *
     * <p>Trying the next station on failure — rather than pre-filtering on availability — is
     * deliberate. Availability read a microsecond ago is a guess; the only honest test is attempting
     * the claim.
     */
    public LockerAssignment reserveNearest(Parcel parcel, double latitude, double longitude) {
        for (LockerLocation location : nearestStations(latitude, longitude, locations.size())) {
            try {
                return reserve(parcel, location.id());
            } catch (NoLockerAvailableException ignored) {
                // Station full between ranking and claiming. Next.
            }
        }
        throw new NoLockerAvailableException(
                "No free " + parcel.size() + "-capable door anywhere in " + network);
    }

    // ---------------------------------------------------------------- courier: fill the door

    /**
     * Records the parcel as physically inside and mints the pickup code.
     *
     * <p>Returning the plaintext is a demo affordance. In production the code goes to the listeners
     * and nowhere else — the moment it becomes a return value, it becomes a response body, and then
     * a log line.
     */
    public String dropOff(String reservationId) {
        Locker locker = lockerByReservationId.get(reservationId);
        if (locker == null) {
            // Deliberately one branch: once a door is reclaimed the reservation leaves the index, so
            // "never existed" and "expired an hour ago" are indistinguishable without keeping history.
            throw new IllegalStateException(
                    "Reservation " + reservationId + " is not holding a door - it was never issued,"
                            + " or the drop-off window elapsed and the door was reclaimed");
        }

        Reservation current = locker.currentReservation();
        if (current == null || !current.id().equals(reservationId)) {
            throw new IllegalStateException(
                    "Reservation " + reservationId + " is no longer holding a door - it was reclaimed");
        }
        if (current.status() != ReservationStatus.AWAITING_DROP_OFF) {
            throw new IllegalStateException("Door " + locker.id() + " is already " + current.status());
        }

        Instant now = clock.instant();
        if (current.hasExpiredAt(now)) {
            expire(locker, current, "courier missed the drop-off window");
            throw new IllegalStateException("Drop-off window elapsed for " + reservationId);
        }

        String plaintextCode = accessCodePolicy.generate();
        Reservation dropped = current.droppedOff(AccessCode.of(plaintextCode), now.plus(retentionWindow));

        if (!locker.tryTransition(current, dropped)) {
            // Lost the CAS: a concurrent sweep reclaimed the door out from under this drop-off.
            throw new IllegalStateException("Door " + locker.id() + " changed state concurrently");
        }

        publish(listener -> listener.onParcelDroppedOff(locker, dropped, plaintextCode));
        return plaintextCode;
    }

    // ---------------------------------------------------------------- customer: empty the door

    /**
     * Opens a door if the code is right, the window is open, and nobody has been guessing.
     *
     * <p><b>Order of the guards is the security design.</b> Lockout is checked before the code, so a
     * blocked keypad cannot be probed. Expiry is checked before the code, so a stale SMS opens
     * nothing even if it is correct. The code check itself is constant-time inside
     * {@link AccessCode}.
     *
     * <p><b>And the last guard is a CAS.</b> Two people entering the correct code at the same instant
     * both reach the release; {@link Locker#tryRelease} hands the parcel to exactly one of them.
     *
     * <p><b>What production would change.</b> The reasons below are distinguishable, which is handy
     * for a demo and wrong for a keypad: a passerby could enumerate which doors hold parcels. Real
     * hardware shows one message for every failure and reports the reason only to the operator.
     */
    public Parcel pickUp(String lockerId, String code) {
        Locker locker = lockerById.get(lockerId);
        if (locker == null) {
            throw new IllegalArgumentException("Unknown door: " + lockerId);
        }

        if (locker.isLockedOut(maxFailedAttempts)) {
            throw new AccessDeniedException(AccessDeniedException.Reason.LOCKED_OUT,
                    "Door " + lockerId + " is locked after " + locker.failedAttempts()
                            + " failed attempts - contact support");
        }

        Reservation current = locker.currentReservation();
        if (current == null || current.status() != ReservationStatus.AWAITING_PICKUP) {
            throw new AccessDeniedException(AccessDeniedException.Reason.EMPTY,
                    "Door " + lockerId + " holds nothing to collect");
        }

        if (current.hasExpiredAt(clock.instant())) {
            expire(locker, current, "retention window elapsed");
            throw new AccessDeniedException(AccessDeniedException.Reason.EXPIRED,
                    "Retention window elapsed; parcel " + current.parcel().trackingId()
                            + " has been returned to the carrier");
        }

        if (!current.accessCode().matches(code)) {
            int attempts = locker.recordFailedAttempt();
            boolean lockedOut = locker.isLockedOut(maxFailedAttempts);
            publish(listener -> listener.onAccessDenied(locker, attempts, lockedOut));
            throw new AccessDeniedException(AccessDeniedException.Reason.WRONG_CODE,
                    "Invalid code (attempts left: " + Math.max(0, maxFailedAttempts - attempts) + ")");
        }

        Reservation collected = current.pickedUp();
        if (!locker.tryRelease(current)) {
            throw new AccessDeniedException(AccessDeniedException.Reason.EMPTY,
                    "Door " + lockerId + " was already opened");
        }

        lockerByReservationId.remove(current.id());
        publish(listener -> listener.onParcelPickedUp(locker, collected));
        return collected.parcel();
    }

    // ---------------------------------------------------------------- operations

    /**
     * Frees every door whose window has elapsed. Run it on a scheduler.
     *
     * <p><b>Why a sweep and not lazy expiry.</b> The booking system's seat holds expire lazily,
     * because a hold nobody asks about costs nothing. A locker is the opposite: an expired parcel is
     * a physical object occupying a door, and nobody will ever ask about it again. Somebody has to go
     * and look, and the parcel has to physically go back to the carrier.
     *
     * @return how many doors were reclaimed
     */
    public int reclaimExpired() {
        Instant now = clock.instant();
        int reclaimed = 0;
        for (Locker locker : lockerById.values()) {
            Reservation current = locker.currentReservation();
            if (current != null && current.hasExpiredAt(now)) {
                String reason = current.status() == ReservationStatus.AWAITING_DROP_OFF
                        ? "courier no-show"
                        : "not collected in time";
                if (expire(locker, current, reason)) {
                    reclaimed++;
                }
            }
        }
        return reclaimed;
    }

    /** Support desk: a customer proved their identity, so let the keypad accept codes again. */
    public void clearLockout(String lockerId) {
        Locker locker = lockerById.get(lockerId);
        if (locker == null) {
            throw new IllegalArgumentException("Unknown door: " + lockerId);
        }
        locker.clearLockout();
    }

    public List<LockerLocation> nearestStations(double latitude, double longitude, int limit) {
        return locations.values().stream()
                .sorted(Comparator.comparingDouble(station -> station.distanceKmTo(latitude, longitude)))
                .limit(limit)
                .toList();
    }

    public Map<LockerSize, Long> availability(String locationId) {
        LockerLocation location = locations.get(locationId);
        if (location == null) {
            throw new IllegalArgumentException("Unknown station: " + locationId);
        }
        return location.availability();
    }

    public void addListener(LockerEventListener listener) {
        listeners.add(listener);
    }

    public void setAllocationStrategy(LockerAllocationStrategy allocationStrategy) {
        this.allocationStrategy = Objects.requireNonNull(allocationStrategy);
    }

    public void setAccessCodePolicy(AccessCodePolicy accessCodePolicy) {
        this.accessCodePolicy = Objects.requireNonNull(accessCodePolicy);
    }

    // ---------------------------------------------------------------- internals

    /** CAS-guarded, so a sweep and a pickup racing on the same door produce exactly one outcome. */
    private boolean expire(Locker locker, Reservation current, String reason) {
        Reservation expired = current.expired();
        if (!locker.tryRelease(current)) {
            return false;
        }
        // Otherwise the index grows forever — a slow leak that only shows up in production.
        lockerByReservationId.remove(current.id());
        publish(listener -> listener.onReservationExpired(locker, expired, reason));
        return true;
    }

    /** A broken listener must not strand a parcel. In production this would also be async. */
    private void publish(Consumer<LockerEventListener> event) {
        for (LockerEventListener listener : listeners) {
            try {
                event.accept(listener);
            } catch (RuntimeException ex) {
                System.err.println("Listener failed: " + ex.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- construction

    /** BUILDER — two windows, two strategies, an attempt cap and a station layout. */
    public static class Builder {

        private final String network;
        private final Map<String, LockerLocation> locations = new LinkedHashMap<>();
        private Clock clock = Clock.systemUTC();
        private Duration dropOffWindow = Duration.ofHours(12);
        private Duration retentionWindow = Duration.ofDays(3);
        private int maxFailedAttempts = 3;
        private LockerAllocationStrategy allocationStrategy = new LockerAllocationStrategy.SmallestFit();
        private AccessCodePolicy accessCodePolicy = new AccessCodePolicy.NumericPin(6);

        public Builder(String network) {
            this.network = network;
        }

        /** e.g. {@code addStation("BLR-01", "Indiranagar", 12.97, 77.64, Map.of(SMALL, 12, LARGE, 2))} */
        public Builder addStation(
                String id, String name, double latitude, double longitude, Map<LockerSize, Integer> layout) {
            List<Locker> lockers = new ArrayList<>();
            for (LockerSize size : LockerSize.values()) {
                int count = layout.getOrDefault(size, 0);
                for (int i = 1; i <= count; i++) {
                    lockers.add(new Locker(id + "-" + size.name().charAt(0) + i, size));
                }
            }
            locations.put(id, new LockerLocation(id, name, latitude, longitude, lockers));
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder dropOffWindow(Duration dropOffWindow) {
            this.dropOffWindow = dropOffWindow;
            return this;
        }

        public Builder retentionWindow(Duration retentionWindow) {
            this.retentionWindow = retentionWindow;
            return this;
        }

        public Builder maxFailedAttempts(int maxFailedAttempts) {
            this.maxFailedAttempts = maxFailedAttempts;
            return this;
        }

        public Builder allocationStrategy(LockerAllocationStrategy allocationStrategy) {
            this.allocationStrategy = allocationStrategy;
            return this;
        }

        public Builder accessCodePolicy(AccessCodePolicy accessCodePolicy) {
            this.accessCodePolicy = accessCodePolicy;
            return this;
        }

        /** Validation lives here, so a LockerService cannot exist in an invalid state. */
        public LockerService build() {
            if (locations.isEmpty()) {
                throw new IllegalStateException("A network needs at least one station");
            }
            if (maxFailedAttempts < 1) {
                throw new IllegalStateException("maxFailedAttempts must be at least 1");
            }
            return new LockerService(this);
        }
    }

    /** Unchecked: a full station is an expected outcome the caller reroutes around. */
    public static class NoLockerAvailableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NoLockerAvailableException(String message) {
            super(message);
        }
    }

    /** Every way a door can refuse to open, with the reason kept out of the customer-facing text. */
    public static class AccessDeniedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public enum Reason {
            WRONG_CODE, LOCKED_OUT, EXPIRED, EMPTY
        }

        private final Reason reason;

        public AccessDeniedException(Reason reason, String message) {
            super(message);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }
}
