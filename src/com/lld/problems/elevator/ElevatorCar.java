package com.lld.problems.elevator;

import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;

/**
 * One lift car, implementing the SCAN ("elevator") algorithm.
 *
 * <p><b>Why SCAN and not a FIFO queue.</b> The obvious model is a {@code Queue<Integer>} of stops
 * served in the order pressed. Try it: a car at floor 0 with requests for 9 then 1 travels
 * 0&rarr;9&rarr;1, passing floor 1 twice and making that passenger wait 17 hops. SCAN keeps
 * travelling in one direction, serving everything on the way, then reverses. Same requests:
 * 0&rarr;1&rarr;9, and nobody is passed by.
 *
 * <p><b>The data structure is the answer.</b> Two sorted sets — stops above and stops below — give
 * "the next stop in my current direction" in O(log n) and make duplicate presses idempotent for
 * free (a {@code Set} ignores the second press of the same button, which is exactly the real
 * behaviour).
 *
 * <p>Interviewers often stop caring about patterns at this point and start caring about this. Be
 * ready to defend the choice.
 */
public class ElevatorCar {

    private final int id;
    private final int minFloor;
    private final int maxFloor;

    private int currentFloor;
    private Direction direction = Direction.IDLE;
    private boolean doorsOpen;

    private final NavigableSet<Integer> stopsAbove = new TreeSet<>();
    private final NavigableSet<Integer> stopsBelow = new TreeSet<>();

    public ElevatorCar(int id, int minFloor, int maxFloor, int startFloor) {
        this.id = id;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.currentFloor = startFloor;
    }

    // ---------------------------------------------------------------- commands

    /** Idempotent: pressing 7 twice adds one stop. */
    public void addStop(int floor) {
        if (floor < minFloor || floor > maxFloor) {
            throw new IllegalArgumentException("Floor " + floor + " is outside " + minFloor + ".." + maxFloor);
        }
        if (floor == currentFloor) {
            doorsOpen = true;
            return;
        }
        if (floor > currentFloor) {
            stopsAbove.add(floor);
        } else {
            stopsBelow.add(floor);
        }
        normaliseDirection();
    }

    /**
     * Advances the simulation by one floor.
     *
     * @return the floor where the doors opened, or empty if the car just passed through / is idle
     */
    public Optional<Integer> step() {
        doorsOpen = false;
        normaliseDirection();
        if (direction == Direction.IDLE) {
            return Optional.empty();
        }

        boolean stopped;
        if (direction == Direction.UP) {
            currentFloor++;
            stopped = stopsAbove.remove(currentFloor);
        } else {
            currentFloor--;
            stopped = stopsBelow.remove(currentFloor);
        }

        normaliseDirection();
        if (stopped) {
            doorsOpen = true;
            return Optional.of(currentFloor);
        }
        return Optional.empty();
    }

    /**
     * Keeps {@link #direction} consistent with the pending stops. Called before and after every
     * move so the car can never be "travelling UP" with nothing above it — the invariant that makes
     * {@link #step()} safe without any extra bounds checks.
     */
    private void normaliseDirection() {
        if (direction == Direction.UP && stopsAbove.isEmpty()) {
            direction = stopsBelow.isEmpty() ? Direction.IDLE : Direction.DOWN;
        } else if (direction == Direction.DOWN && stopsBelow.isEmpty()) {
            direction = stopsAbove.isEmpty() ? Direction.IDLE : Direction.UP;
        } else if (direction == Direction.IDLE) {
            if (!stopsAbove.isEmpty()) {
                direction = Direction.UP;
            } else if (!stopsBelow.isEmpty()) {
                direction = Direction.DOWN;
            }
        }
    }

    // ---------------------------------------------------------------- queries

    public int id() {
        return id;
    }

    public int currentFloor() {
        return currentFloor;
    }

    public Direction direction() {
        return direction;
    }

    public boolean doorsOpen() {
        return doorsOpen;
    }

    public boolean isIdle() {
        return direction == Direction.IDLE;
    }

    public int pendingStops() {
        return stopsAbove.size() + stopsBelow.size();
    }

    public boolean servesFloor(int floor) {
        return stopsAbove.contains(floor) || stopsBelow.contains(floor);
    }

    @Override
    public String toString() {
        return String.format("car%d @%-2d %s %s stops=%s%s",
                id, currentFloor, direction.symbol(),
                doorsOpen ? "[||]" : "[  ]",
                stopsBelow.descendingSet(), stopsAbove);
    }
}
