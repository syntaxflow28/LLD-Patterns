package problems.parkinglot;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * FACADE over the whole subsystem. Two public operations: {@link #park} and {@link #unpark}.
 *
 * <p><b>Why this is not a Singleton.</b> The textbook answer makes {@code ParkingLot} a singleton
 * because "there is only one lot". But (a) a chain operates many lots, (b) a private constructor
 * plus global state makes it untestable, and (c) uniqueness is a deployment concern, not a class
 * concern. Say: <em>"I'd have one instance, but I'd get that by constructing it once in the
 * composition root and injecting it, rather than by making the class enforce it."</em> That answer
 * scores higher than reciting double-checked locking.
 *
 * <p>{@link Clock} is injected for the same reason: a fee calculation you cannot test without
 * sleeping for an hour is a fee calculation you will not test.
 */
public class ParkingLot {

    private final String name;
    private final List<ParkingFloor> floors;
    private final Clock clock;

    /** volatile: swappable at runtime (weekend tariff kicks in) and safely published to readers. */
    private volatile FeeStrategy feeStrategy;
    private volatile SpotAllocationStrategy allocationStrategy;

    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();
    private final List<ParkingLotListener> listeners = new CopyOnWriteArrayList<>();

    private ParkingLot(Builder builder) {
        this.name = builder.name;
        this.floors = List.copyOf(builder.floors);
        this.clock = builder.clock;
        this.feeStrategy = builder.feeStrategy;
        this.allocationStrategy = builder.allocationStrategy;
    }

    // ---------------------------------------------------------------- operations

    /**
     * Claims a spot and issues a ticket.
     *
     * @throws NoSpotAvailableException if nothing fits this vehicle
     */
    public Ticket park(Vehicle vehicle) {
        Objects.requireNonNull(vehicle, "vehicle");

        ParkingSpot spot = allocationStrategy.allocate(floors, vehicle)
                .orElseThrow(() -> new NoSpotAvailableException(
                        "Lot full for vehicle type " + vehicle.type()));

        Ticket ticket = new Ticket(
                UUID.randomUUID().toString().substring(0, 8),
                vehicle,
                spot,
                clock.instant());

        activeTickets.put(ticket.id(), ticket);
        publish(listener -> listener.onVehicleEntered(ticket));
        return ticket;
    }

    /**
     * Settles a ticket, frees the spot and returns the fee.
     *
     * <p>Note the order: {@code remove} first. {@code ConcurrentHashMap.remove} returns non-null to
     * exactly one caller, so a double-scan of the same ticket at the exit barrier cannot release
     * the spot twice or bill twice. Doing {@code get} then {@code remove} would reintroduce the
     * race.
     */
    public BigDecimal unpark(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Unknown or already-settled ticket: " + ticketId);
        }

        BigDecimal fee = feeStrategy.fee(ticket, clock.instant());
        ticket.spot().release();
        publish(listener -> listener.onVehicleExited(ticket, fee));
        return fee;
    }

    // ---------------------------------------------------------------- queries & wiring

    public Map<SpotType, Long> availability() {
        Map<SpotType, Long> counts = new EnumMap<>(SpotType.class);
        for (SpotType type : SpotType.values()) {
            long free = floors.stream().mapToLong(floor -> floor.freeCount(type)).sum();
            counts.put(type, free);
        }
        return counts;
    }

    public int occupiedCount() {
        return activeTickets.size();
    }

    public String name() {
        return name;
    }

    public void addListener(ParkingLotListener listener) {
        listeners.add(listener);
    }

    public void setFeeStrategy(FeeStrategy feeStrategy) {
        this.feeStrategy = Objects.requireNonNull(feeStrategy);
    }

    public void setAllocationStrategy(SpotAllocationStrategy allocationStrategy) {
        this.allocationStrategy = Objects.requireNonNull(allocationStrategy);
    }

    /**
     * A misbehaving listener must not break parking. In production this would also be async so a
     * slow subscriber cannot stall the entry barrier.
     */
    private void publish(java.util.function.Consumer<ParkingLotListener> event) {
        for (ParkingLotListener listener : listeners) {
            try {
                event.accept(listener);
            } catch (RuntimeException ex) {
                System.err.println("Listener failed: " + ex.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- construction

    /** BUILDER — the lot has many optional knobs and a floor layout that reads badly as arguments. */
    public static class Builder {

        private final String name;
        private final List<ParkingFloor> floors = new ArrayList<>();
        private Clock clock = Clock.systemUTC();
        private FeeStrategy feeStrategy;
        private SpotAllocationStrategy allocationStrategy = new SpotAllocationStrategy.NearestFirst();

        public Builder(String name) {
            this.name = name;
        }

        /** e.g. {@code addFloor(0, Map.of(SpotType.COMPACT, 20, SpotType.LARGE, 4))} */
        public Builder addFloor(int number, Map<SpotType, Integer> layout) {
            List<ParkingSpot> spots = new ArrayList<>();
            for (Map.Entry<SpotType, Integer> entry : layout.entrySet()) {
                for (int i = 1; i <= entry.getValue(); i++) {
                    spots.add(new ParkingSpot(
                            "F" + number + "-" + entry.getKey().name().charAt(0) + i,
                            number,
                            entry.getKey()));
                }
            }
            floors.add(new ParkingFloor(number, spots));
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder feeStrategy(FeeStrategy feeStrategy) {
            this.feeStrategy = feeStrategy;
            return this;
        }

        public Builder allocationStrategy(SpotAllocationStrategy allocationStrategy) {
            this.allocationStrategy = allocationStrategy;
            return this;
        }

        /** Validation belongs in build(), so a ParkingLot cannot exist in an invalid state. */
        public ParkingLot build() {
            if (floors.isEmpty()) {
                throw new IllegalStateException("A lot needs at least one floor");
            }
            if (feeStrategy == null) {
                throw new IllegalStateException("A lot needs a fee strategy");
            }
            return new ParkingLot(this);
        }
    }

    /** Unchecked: a full lot is an expected outcome the caller handles, not a programming error. */
    public static class NoSpotAvailableException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public NoSpotAvailableException(String message) {
            super(message);
        }
    }
}
