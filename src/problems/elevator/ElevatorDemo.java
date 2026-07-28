package problems.elevator;

/**
 * Runnable walk-through of the Elevator System design.
 *
 * <pre>
 *   java -cp out problems.elevator.ElevatorDemo
 * </pre>
 *
 * <p>Reading the trace: each tick prints every car as
 * {@code carN @floor direction [doors] stops=[below][above]}.
 *
 * <p>Patterns and the requirement behind each:
 * <ul>
 *   <li><b>Strategy</b> &larr; "which lift answers the call?" — the tunable business policy</li>
 *   <li><b>Mediator</b> &larr; cars must not coordinate with each other</li>
 *   <li><b>Facade</b> &larr; the outside world presses buttons; it does not drive motors</li>
 *   <li><b>State-ish Direction enum</b> &larr; UP/DOWN/IDLE with an invariant, not a boolean</li>
 *   <li><b>Sealed hierarchy</b> &larr; hall calls and car calls are genuinely different requests</li>
 * </ul>
 *
 * <p>The real content, though, is the SCAN algorithm in {@link ElevatorCar}. Have the pattern
 * answer ready in one minute, then spend the rest of the interview on scheduling.
 */
public class ElevatorDemo {

    public static void main(String[] args) {
        section("1. SCAN vs FIFO - one car, calls for floor 9 then floor 1");
        System.out.println("  FIFO would go 0->9->1 (17 hops, passing floor 1 twice).");
        System.out.println("  SCAN goes 0->1->9 (9 hops, nobody is passed by):");
        ElevatorSystem single = new ElevatorSystem(1, 0, 10, new SchedulingStrategy.NearestCar());
        single.requestCar(1, 9);
        single.requestCar(1, 1);
        single.runUntilIdle(20);

        section("2. Three cars, NEAREST_CAR dispatch");
        ElevatorSystem building = new ElevatorSystem(3, 0, 10, new SchedulingStrategy.NearestCar());
        building.requestHall(5, Direction.UP);   // everyone idle at 0, tie -> car1
        building.requestHall(8, Direction.DOWN); // car1 is heading UP, so it pays the turnaround penalty -> car2
        building.requestHall(2, Direction.UP);   // car1 is already going up past 2, so it is as cheap as an idle car
        building.tick(3);
        System.out.println("  ties go to the first car here; a production dispatcher would");
        System.out.println("  break ties on load, which is exactly what LEAST_BUSY does below.");

        section("3. Passengers board and press their destinations");
        building.requestCar(1, 7);
        building.requestCar(3, 4);
        building.runUntilIdle(30);
        System.out.println("  all idle: " + building.allIdle());

        section("4. Why direction matters - a car 1 floor away can still be the wrong car");
        ElevatorSystem tricky = new ElevatorSystem(2, 0, 10, new SchedulingStrategy.NearestCar());
        tricky.requestCar(1, 10);                 // car1 committed to a long run upward
        tricky.tick(4);                           // car1 is now around floor 4, heading UP
        System.out.println("  car1 is at floor " + tricky.cars().get(0).currentFloor()
                + " heading " + tricky.cars().get(0).direction()
                + "; car2 is idle at floor " + tricky.cars().get(1).currentFloor());
        System.out.println("  a DOWN call from floor 5 is 1 floor from car1 but 5 from car2:");
        tricky.requestHall(5, Direction.DOWN);
        System.out.println("  -> car2 wins, because car1 must finish its sweep and turn around");
        tricky.runUntilIdle(30);

        section("5. Swap the dispatch policy at runtime");
        ElevatorSystem tuned = new ElevatorSystem(3, 0, 10, new SchedulingStrategy.NearestCar());
        tuned.requestHall(1, Direction.UP);
        tuned.requestHall(2, Direction.UP);
        tuned.requestHall(3, Direction.UP);
        System.out.println("  NEAREST_CAR piles work onto whichever car is closest:");
        printLoad(tuned);

        tuned.setStrategy(new SchedulingStrategy.LeastBusy());
        tuned.requestHall(4, Direction.UP);
        tuned.requestHall(6, Direction.UP);
        System.out.println("  LEAST_BUSY spreads it out:");
        printLoad(tuned);
        tuned.runUntilIdle(40);

        section("6. Invalid input is rejected");
        expectFailure("floor 99 in a 0..10 building", () -> tuned.requestHall(99, Direction.UP));
        expectFailure("a hall call with no direction", () -> tuned.requestHall(3, Direction.IDLE));
        expectFailure("a car that does not exist", () -> tuned.requestCar(9, 3));

        System.out.println("\nDone.");
    }

    private static void printLoad(ElevatorSystem system) {
        system.cars().forEach(car -> System.out.println("     " + car));
    }

    private static void expectFailure(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + " -> UNEXPECTEDLY ACCEPTED");
        } catch (RuntimeException ex) {
            System.out.println("  " + label + " -> rejected: " + ex.getMessage());
        }
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
