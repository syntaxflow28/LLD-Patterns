package problems.parkinglot;

import java.util.Objects;

/**
 * A vehicle is a value object: two vehicles with the same plate <em>are</em> the same vehicle.
 * A {@code record} gives us equals/hashCode/toString for free and makes immutability the default.
 */
public record Vehicle(String licensePlate, VehicleType type) {

    public Vehicle {
        Objects.requireNonNull(licensePlate, "licensePlate");
        Objects.requireNonNull(type, "type");
        if (licensePlate.isBlank()) {
            throw new IllegalArgumentException("licensePlate must not be blank");
        }
    }
}
