package patterns.practical.circuitbreaker;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * CIRCUIT BREAKER — stop calling a dependency that is already failing.
 *
 * <p>The problem: a downstream service goes down. Every request still tries it, waits for the
 * timeout, then fails. Threads pile up waiting on a call that cannot succeed, the thread pool
 * exhausts, and <b>your</b> service goes down too. That is cascading failure, and retries make it
 * worse rather than better.
 *
 * <p>The breaker wraps the call and watches the failure rate. Past a threshold it "opens" and fails
 * every subsequent call <em>instantly</em>, without touching the network. After a cooldown it lets
 * one probe through to see whether the dependency recovered.
 *
 * <p>It is a State machine with exactly three states, and interviewers expect all three by name:
 * <pre>
 *   CLOSED     normal. Calls pass through; consecutive failures are counted.
 *      |  failureThreshold consecutive failures
 *      v
 *   OPEN       fail fast. No calls reach the dependency at all.
 *      |  openDuration elapses
 *      v
 *   HALF_OPEN  probing. A limited number of trial calls are allowed through.
 *      |                              \
 *      |  successThreshold successes   \  any failure
 *      v                                v
 *   CLOSED                             OPEN
 * </pre>
 *
 * <p><b>Why HALF_OPEN exists</b> is the question that separates people who have read about this from
 * people who have used it. Without it, when the cooldown ends you dump the full production load onto
 * a service that has been down for 30 seconds and has a cold cache and an empty connection pool —
 * and you knock it straight back over. HALF_OPEN lets exactly one or two requests test the water.
 */
enum CircuitState {
    CLOSED, OPEN, HALF_OPEN
}

/** Thrown instead of calling the dependency. Distinct type so callers can serve a fallback. */
class CircuitOpenException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    CircuitOpenException(String message) {
        super(message);
    }
}

class CircuitBreaker {

    private final String name;
    private final int failureThreshold;   // consecutive failures that trip the breaker
    private final int successThreshold;   // consecutive HALF_OPEN successes that close it
    private final long openDurationMillis;
    private final LongSupplier clock;

    private CircuitState state = CircuitState.CLOSED;
    private int consecutiveFailures;
    private int consecutiveSuccesses;
    private long openedAtMillis;

    CircuitBreaker(String name, int failureThreshold, int successThreshold,
                   long openDurationMillis, LongSupplier clock) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.openDurationMillis = openDurationMillis;
        this.clock = clock;
    }

    /**
     * Runs the call through the breaker.
     *
     * <p><b>Note what is NOT synchronized: the call itself.</b> Holding the lock across a network
     * call would serialise every request in the process behind one monitor — the breaker would
     * become a worse bottleneck than the outage it was protecting against. Only the state
     * transitions are guarded. Getting this boundary right is the concurrency point of this pattern.
     */
    <T> T call(Supplier<T> action) {
        beforeCall();
        try {
            T result = action.get();
            onSuccess();
            return result;
        } catch (RuntimeException failure) {
            onFailure();
            throw failure;
        }
    }

    /** Convenience: fail fast, but serve a degraded response instead of an exception. */
    <T> T callOrFallback(Supplier<T> action, Supplier<T> fallback) {
        try {
            return call(action);
        } catch (RuntimeException failure) {
            return fallback.get();
        }
    }

    private synchronized void beforeCall() {
        if (state == CircuitState.OPEN) {
            if (clock.getAsLong() - openedAtMillis < openDurationMillis) {
                throw new CircuitOpenException(name + " circuit is OPEN - failing fast");
            }
            // Cooldown elapsed. Let a probe through rather than the whole flood.
            state = CircuitState.HALF_OPEN;
            consecutiveSuccesses = 0;
        }
    }

    private synchronized void onSuccess() {
        if (state == CircuitState.HALF_OPEN) {
            consecutiveSuccesses++;
            if (consecutiveSuccesses >= successThreshold) {
                state = CircuitState.CLOSED;
                consecutiveFailures = 0;
            }
            return;
        }
        // A single success resets the counter, because the threshold counts CONSECUTIVE failures.
        // Production breakers usually use a rolling failure RATE instead: with a consecutive counter,
        // a dependency failing 50% of the time never trips, which is arguably worse than being down.
        consecutiveFailures = 0;
    }

    private synchronized void onFailure() {
        if (state == CircuitState.HALF_OPEN) {
            trip(); // the probe failed - straight back to OPEN, do not pass go
            return;
        }
        consecutiveFailures++;
        if (consecutiveFailures >= failureThreshold) {
            trip();
        }
    }

    private void trip() {
        state = CircuitState.OPEN;
        openedAtMillis = clock.getAsLong();
        consecutiveSuccesses = 0;
    }

    synchronized CircuitState state() {
        return state;
    }
}

public class CircuitBreakerDemo {

    public static void main(String[] args) {
        TestClock clock = new TestClock();
        FlakyService service = new FlakyService();
        // Trips after 3 consecutive failures, stays open 5s, needs 2 clean probes to recover.
        CircuitBreaker breaker = new CircuitBreaker("payments", 3, 2, 5_000, clock);

        section("1. Dependency is healthy - calls pass straight through");
        for (int i = 0; i < 3; i++) {
            attempt(breaker, service, clock);
        }

        section("2. Dependency breaks - 3 consecutive failures trip the breaker");
        service.broken = true;
        for (int i = 0; i < 3; i++) {
            attempt(breaker, service, clock);
        }
        System.out.println("  state is now " + breaker.state());

        section("3. While OPEN, nothing reaches the dependency at all");
        int before = service.calls;
        for (int i = 0; i < 5; i++) {
            attempt(breaker, service, clock);
        }
        System.out.println("  calls that actually reached the service: " + (service.calls - before) + " of 5");
        System.out.println("  This is the whole point: no timeouts, no threads parked, no cascade.");

        section("4. Cooldown elapses - ONE probe is allowed through (HALF_OPEN)");
        clock.advance(5_100);
        System.out.println("  +5.1s, service is still broken");
        attempt(breaker, service, clock);
        System.out.println("  state is now " + breaker.state() + " - the failed probe re-opened it immediately");
        System.out.println("  Without HALF_OPEN we would have dumped full production load onto a service");
        System.out.println("  that has been down for 5 seconds and would knock it straight back over.");

        section("5. Dependency recovers - probes succeed and the circuit closes");
        service.broken = false;
        clock.advance(5_100);
        attempt(breaker, service, clock);
        System.out.println("  state after 1 good probe : " + breaker.state() + " (needs 2)");
        attempt(breaker, service, clock);
        System.out.println("  state after 2 good probes: " + breaker.state());

        section("6. Fallbacks: degrade instead of erroring");
        service.broken = true;
        String answer = null;
        for (int i = 0; i < 4; i++) {
            attempts++;
            if (breaker.state() == CircuitState.OPEN) {
                rejected++;
            }
            answer = breaker.callOrFallback(service::charge, () -> "queued for later");
        }
        System.out.println("  breaker is " + breaker.state() + ", caller got: \"" + answer + "\"");
        System.out.println("  A cached, stale or queued response beats a 500. The breaker is what makes");
        System.out.println("  that decision cheap enough to take on every request.");

        section("Summary");
        System.out.println("  total attempts made by the caller : " + attempts);
        System.out.println("  rejected without a network call   : " + rejected);
        System.out.println("  calls that reached the dependency : " + service.calls);
        System.out.println("  Every rejection was a timeout the caller did not wait for, and a");
        System.out.println("  connection the struggling dependency did not have to serve.");

        System.out.println("\nDone.");
    }

    private static int attempts;
    private static int rejected;

    private static void attempt(CircuitBreaker breaker, FlakyService service, TestClock clock) {
        attempts++;
        clock.advance(50);
        try {
            System.out.printf("  [%-9s] %s%n", breaker.state(), breaker.call(service::charge));
        } catch (CircuitOpenException rejectedFast) {
            rejected++;
            System.out.printf("  [%-9s] REJECTED - %s%n", breaker.state(), rejectedFast.getMessage());
        } catch (RuntimeException failed) {
            System.out.printf("  [%-9s] FAILED   - %s%n", breaker.state(), failed.getMessage());
        }
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    /** Stands in for a downstream service. {@code calls} proves the breaker really is short-circuiting. */
    static final class FlakyService {
        boolean broken;
        int calls;

        String charge() {
            calls++;
            if (broken) {
                throw new IllegalStateException("payment gateway timeout");
            }
            return "charged OK";
        }
    }

    /** Time under test control, so a 5 second cooldown does not cost 5 seconds. */
    static final class TestClock implements LongSupplier {
        private long millis;

        void advance(long delta) {
            millis += delta;
        }

        @Override
        public long getAsLong() {
            return millis;
        }
    }
}
