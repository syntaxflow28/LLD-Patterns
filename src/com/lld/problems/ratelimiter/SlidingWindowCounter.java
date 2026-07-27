package com.lld.problems.ratelimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SLIDING WINDOW COUNTER — the production answer, and the one to lead with.
 *
 * <p>It keeps only two numbers per client (this window's count and the previous window's) and
 * blends them by how far into the current window we are:
 *
 * <pre>
 *   estimate = previousCount * (1 - elapsedFractionOfCurrentWindow) + currentCount
 * </pre>
 *
 * <p>Read that formula out loud in words, because that is what convinces an interviewer you
 * understand it rather than remember it: <em>"if I'm 25% into the current window, then 75% of the
 * previous window is still inside my sliding view, so I count 75% of its requests."</em>
 *
 * <p><b>What it buys.</b> O(1) memory like the fixed window, and it kills the boundary burst,
 * because immediately after a window flips the previous window's count is weighted at nearly 100%
 * and blocks the second volley. The same trace that lets 2x through a fixed window is capped here.
 *
 * <p><b>What it costs.</b> It is an approximation — it assumes the previous window's requests were
 * spread evenly across it. If they were actually all bunched at the very start, the estimate
 * over-counts and rejects a few requests it did not have to. Cloudflare published measurements
 * showing the error is well under 1% on real traffic, which is why this is what large edges
 * actually run. Volunteering "it is an approximation, and here is why the error is acceptable" is
 * the senior-level version of this answer.
 */
public class SlidingWindowCounter implements RateLimiter {

    private record Buckets(long currentWindowStart, int currentCount, int previousCount) {
    }

    private final int limit;
    private final long windowMillis;
    private final TimeSource time;
    private final Map<String, Buckets> state = new ConcurrentHashMap<>();

    public SlidingWindowCounter(int limit, long windowMillis, TimeSource time) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.time = time;
    }

    @Override
    public boolean allow(String clientId) {
        long now = time.millis();
        long windowStart = now - (now % windowMillis);
        boolean[] allowed = new boolean[1];

        state.compute(clientId, (key, existing) -> {
            Buckets rolled = roll(existing, windowStart);

            double elapsedFraction = (now - windowStart) / (double) windowMillis;
            double estimate = rolled.previousCount() * (1.0 - elapsedFraction) + rolled.currentCount();

            if (estimate < limit) {
                allowed[0] = true;
                return new Buckets(windowStart, rolled.currentCount() + 1, rolled.previousCount());
            }
            allowed[0] = false;
            return rolled;
        });

        return allowed[0];
    }

    /** Advances the two buckets to the current window, discarding anything older than one window. */
    private Buckets roll(Buckets existing, long windowStart) {
        if (existing == null) {
            return new Buckets(windowStart, 0, 0);
        }
        if (existing.currentWindowStart() == windowStart) {
            return existing;
        }
        if (existing.currentWindowStart() == windowStart - windowMillis) {
            // Exactly one window has passed: today's count becomes yesterday's.
            return new Buckets(windowStart, 0, existing.currentCount());
        }
        // Client went quiet for more than a full window; everything is stale.
        return new Buckets(windowStart, 0, 0);
    }

    @Override
    public String name() {
        return "SLIDING_COUNTER";
    }
}
