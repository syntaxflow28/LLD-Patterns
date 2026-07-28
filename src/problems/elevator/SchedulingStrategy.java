package problems.elevator;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * STRATEGY — which car should answer this hall call?
 *
 * <p>Dispatching is the part of an elevator system that building owners actually tune: a hotel
 * wants shortest wait, an office tower wants throughput at 9am, a hospital wants a car reserved for
 * gurneys, and an energy-saving mode wants to park cars rather than move them. All of that is this
 * one interface, and {@link ElevatorSystem} never changes.
 *
 * <p>The scoring deliberately lives here rather than on {@link ElevatorCar}. The car exposes facts
 * (where am I, where am I going, how busy am I); the strategy decides what those facts are worth.
 * Put the scoring on the car and every new policy edits the car.
 */
public interface SchedulingStrategy {

    Optional<ElevatorCar> select(List<ElevatorCar> cars, Request.Hall call);

    String name();

    /**
     * Classic "nearest car" / shortest-seek dispatch.
     *
     * <p>The subtlety is that raw floor distance is wrong. A car two floors below you heading
     * <em>down</em> is useless — it has to finish its run, reverse, and come back. So a car only
     * gets its true distance if it is idle, or already moving towards you in the direction you want
     * to travel. Everything else pays a penalty plus its outstanding workload.
     */
    final class NearestCar implements SchedulingStrategy {

        /** Bigger than any building, so "on my way" always beats "must turn around". */
        private static final int TURNAROUND_PENALTY = 1000;

        @Override
        public Optional<ElevatorCar> select(List<ElevatorCar> cars, Request.Hall call) {
            return cars.stream().min(Comparator.comparingInt((ElevatorCar car) -> cost(car, call)));
        }

        private int cost(ElevatorCar car, Request.Hall call) {
            int gap = Math.abs(car.currentFloor() - call.floor());

            if (car.isIdle()) {
                return gap;
            }
            boolean approachingFromBelow = car.direction() == Direction.UP
                    && call.direction() == Direction.UP
                    && call.floor() >= car.currentFloor();
            boolean approachingFromAbove = car.direction() == Direction.DOWN
                    && call.direction() == Direction.DOWN
                    && call.floor() <= car.currentFloor();

            if (approachingFromBelow || approachingFromAbove) {
                return gap;
            }
            return TURNAROUND_PENALTY + gap + car.pendingStops();
        }

        @Override
        public String name() {
            return "NEAREST_CAR";
        }
    }

    /**
     * Load balancing: give the call to whoever has the fewest stops queued, distance as tie-break.
     *
     * <p>Worse average wait than NEAREST_CAR, better worst case — it stops one car becoming the
     * "popular" one that everybody waits behind. Being able to state that trade-off is the point of
     * having two implementations.
     */
    final class LeastBusy implements SchedulingStrategy {

        @Override
        public Optional<ElevatorCar> select(List<ElevatorCar> cars, Request.Hall call) {
            return cars.stream().min(Comparator
                    .<ElevatorCar>comparingInt(ElevatorCar::pendingStops)
                    .thenComparingInt((ElevatorCar car) -> Math.abs(car.currentFloor() - call.floor())));
        }

        @Override
        public String name() {
            return "LEAST_BUSY";
        }
    }
}
