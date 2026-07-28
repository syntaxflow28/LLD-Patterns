package problems.elevator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * MEDIATOR + FACADE — the building's controller.
 *
 * <p>Cars do not talk to each other. If they did, "don't send two cars to the same floor" would
 * become an N&times;N conversation and every car would need a reference to every other car. Instead
 * each car knows only its own stops, and this one object owns the cross-car policy. That is the
 * textbook Mediator justification with a concrete pay-off.
 *
 * <p>The controller is also the only thing the outside world touches: press a button, advance time.
 * Two methods, no leaking of {@link ElevatorCar} internals.
 *
 * <p><b>On threading.</b> A real controller runs each car on its own thread with a blocking queue
 * of stops, and the demo's {@link #tick()} would be the motor's timer. Simulating with an explicit
 * tick keeps the output deterministic and reviewable, which is what you want on a whiteboard —
 * say "I'd make each car a task on an executor" rather than actually spawning threads you cannot
 * demonstrate.
 */
public class ElevatorSystem {

    private final List<ElevatorCar> cars;
    private final int minFloor;
    private final int maxFloor;
    private volatile SchedulingStrategy strategy;
    private int clock;

    public ElevatorSystem(int carCount, int minFloor, int maxFloor, SchedulingStrategy strategy) {
        if (carCount < 1) {
            throw new IllegalArgumentException("Need at least one car");
        }
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.strategy = Objects.requireNonNull(strategy);
        this.cars = new ArrayList<>();
        for (int i = 1; i <= carCount; i++) {
            cars.add(new ElevatorCar(i, minFloor, maxFloor, minFloor));
        }
    }

    /** Someone pressed UP or DOWN in the lobby. Returns the car that was assigned. */
    public ElevatorCar requestHall(int floor, Direction direction) {
        validate(floor);
        Request.Hall call = new Request.Hall(floor, direction);

        ElevatorCar chosen = strategy.select(cars, call)
                .orElseThrow(() -> new IllegalStateException("No car available"));
        chosen.addStop(floor);
        System.out.printf("  t%-3d %-28s -> car%d%n", clock, call, chosen.id());
        return chosen;
    }

    /**
     * Someone inside a car pressed a floor button.
     *
     * <p>No scheduling happens here — and that is the point of separating {@link Request.Car} from
     * {@link Request.Hall}. A passenger already on board cannot be reassigned to another lift.
     */
    public void requestCar(int carId, int floor) {
        validate(floor);
        ElevatorCar car = cars.stream()
                .filter(c -> c.id() == carId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No car " + carId));
        car.addStop(floor);
        System.out.printf("  t%-3d %-28s -> car%d%n", clock, new Request.Car(carId, floor), carId);
    }

    /** Advance the whole building by one time step. */
    public void tick() {
        clock++;
        StringBuilder line = new StringBuilder(String.format("  t%-3d ", clock));
        for (ElevatorCar car : cars) {
            Optional<Integer> openedAt = car.step();
            line.append(car).append(openedAt.isPresent() ? " OPEN" : "     ").append("   ");
        }
        System.out.println(line);
    }

    public void tick(int times) {
        for (int i = 0; i < times; i++) {
            tick();
        }
    }

    /** Runs until every car has nothing left to do, with a hard cap so a bug cannot hang the demo. */
    public void runUntilIdle(int maxTicks) {
        int guard = 0;
        while (!allIdle() && guard++ < maxTicks) {
            tick();
        }
    }

    public boolean allIdle() {
        return cars.stream().allMatch(car -> car.isIdle() && car.pendingStops() == 0);
    }

    public List<ElevatorCar> cars() {
        return List.copyOf(cars);
    }

    public void setStrategy(SchedulingStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy);
        System.out.println("  [dispatch policy is now " + strategy.name() + "]");
    }

    private void validate(int floor) {
        if (floor < minFloor || floor > maxFloor) {
            throw new IllegalArgumentException("Floor " + floor + " is outside " + minFloor + ".." + maxFloor);
        }
    }
}
