package problems.parkinglot;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * STRATEGY — which free spot should this vehicle get?
 *
 * <p>The second named axis of change. Real lots differ: some want the nearest spot to the entrance,
 * some want to fill floor by floor, some want to reserve LARGE spots for trucks, some want to
 * spread cars out so doors do not bang. All of that is this interface.
 *
 * <p>Every implementation must claim the spot via {@link ParkingSpot#tryOccupy} and honour a
 * {@code false} result, because two allocators can be running concurrently.
 */
public interface SpotAllocationStrategy {

    /**
     * @return the claimed spot, or empty if the lot is full for this vehicle type
     */
    Optional<ParkingSpot> allocate(List<ParkingFloor> floors, Vehicle vehicle);

    /** Lowest floor first, first fitting spot on that floor. Cheap, O(spots) worst case. */
    final class NearestFirst implements SpotAllocationStrategy {

        @Override
        public Optional<ParkingSpot> allocate(List<ParkingFloor> floors, Vehicle vehicle) {
            for (ParkingFloor floor : floors) {
                for (ParkingSpot spot : floor.spots()) {
                    if (spot.canFit(vehicle.type()) && spot.tryOccupy(vehicle)) {
                        return Optional.of(spot);
                    }
                }
            }
            return Optional.empty();
        }
    }

    /**
     * Smallest spot that fits, tie-broken by lowest floor.
     *
     * <p>Why bother: with {@code NearestFirst}, a motorcycle parked on floor 0 can take a LARGE
     * spot and a truck arriving later is turned away while COMPACT spots sit empty. Best-fit is the
     * answer to "how do you avoid wasting big spots?", which is a very common follow-up.
     */
    final class BestFit implements SpotAllocationStrategy {

        @Override
        public Optional<ParkingSpot> allocate(List<ParkingFloor> floors, Vehicle vehicle) {
            List<ParkingSpot> candidates = floors.stream()
                    .flatMap(floor -> floor.spots().stream())
                    .filter(spot -> spot.canFit(vehicle.type()) && spot.isFree())
                    .sorted(Comparator.comparingInt((ParkingSpot s) -> s.type().size())
                            .thenComparingInt(ParkingSpot::floor))
                    .toList();

            // isFree() above was only a hint; tryOccupy() is the real claim.
            for (ParkingSpot spot : candidates) {
                if (spot.tryOccupy(vehicle)) {
                    return Optional.of(spot);
                }
            }
            return Optional.empty();
        }
    }
}
