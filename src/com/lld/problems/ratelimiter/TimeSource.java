package com.lld.problems.ratelimiter;

/**
 * A source of milliseconds, injected everywhere instead of calling {@link System#currentTimeMillis}.
 *
 * <p>Rate limiting is defined entirely in terms of time, so a rate limiter that reads the wall clock
 * directly cannot be tested — you would have to actually sleep for a second to check a
 * one-second window, and any test that depends on real timing is flaky by construction.
 *
 * <p>This one interface is what lets the demo prove the fixed-window boundary burst deterministically
 * rather than describing it in a comment and hoping.
 *
 * <p><b>Why milliseconds and not {@link java.time.Clock}:</b> {@code Clock} allocates an
 * {@code Instant} per call. A rate limiter sits on the hot path of every single request, so a
 * primitive {@code long} is the right call here — a rare case where the uglier type wins.
 */
@FunctionalInterface
public interface TimeSource {

    long millis();

    static TimeSource system() {
        return System::currentTimeMillis;
    }
}
