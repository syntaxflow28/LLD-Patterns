package com.lld.problems.elevator;

/**
 * The two kinds of request, which behave completely differently — and conflating them is the most
 * common modelling mistake in this problem.
 *
 * <ul>
 *   <li>A {@link Hall} request comes from the buttons in the lobby. It names a floor <em>and a
 *       direction</em>, and no particular car. The scheduler decides who serves it.</li>
 *   <li>A {@link Car} request comes from inside a specific lift. It names a floor and <em>no</em>
 *       direction, and it must be served by that car — you cannot reassign a passenger who is
 *       already on board.</li>
 * </ul>
 *
 * <p>A single {@code Request(floor, direction)} class forces the scheduler to guess which case it
 * is looking at. Modelling them separately makes the API impossible to misuse.
 *
 * <p>A {@code sealed} interface (Java 17) says "these are the only two kinds, forever", which lets
 * the compiler prove a switch is exhaustive.
 */
public sealed interface Request {

    int floor();

    /** Pressed at the lobby: "someone on floor 3 wants to go up." */
    record Hall(int floor, Direction direction) implements Request {

        public Hall {
            if (direction == Direction.IDLE) {
                throw new IllegalArgumentException("A hall call must be UP or DOWN");
            }
        }

        @Override
        public String toString() {
            return "hall call floor " + floor + " going " + direction;
        }
    }

    /** Pressed inside car 2: "take me to floor 7." */
    record Car(int carId, int floor) implements Request {

        @Override
        public String toString() {
            return "car " + carId + " -> floor " + floor;
        }
    }
}
