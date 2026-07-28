package com.lld.problems.ratelimiter;

/**
 * Runnable walk-through of the rate limiter design.
 *
 * <pre>
 *   java -cp out com.lld.problems.ratelimiter.RateLimiterDemo
 * </pre>
 *
 * <p>Read the output as {@code .} = allowed, {@code X} = rejected (HTTP 429).
 *
 * <p>The centrepiece is section 2, which <em>reproduces</em> the fixed-window boundary burst rather
 * than describing it: 10 requests get through a 5-per-second limiter inside 100 milliseconds. Being
 * able to state that failure mode, and then name the O(1) algorithm that fixes it, is what this
 * question is for.
 */
public class RateLimiterDemo {

    private static final long ONE_SECOND = 1000L;
    private static final int LIMIT = 5;

    public static void main(String[] args) {
        ManualClock clock = new ManualClock(0);

        section("1. Baseline: limit is 5 per second, fire 7 at once");
        for (RateLimiter limiter : allAlgorithms(clock)) {
            System.out.printf("  %-16s %s%n", limiter.name(), fire(limiter, "alice", 7));
        }

        section("2. THE FIXED WINDOW BUG: 5 requests at t=1.9s, 5 more at t=2.0s");
        System.out.println("  A 5/second limit should never pass 10 requests inside 100ms.");
        System.out.println();
        for (RateLimiter limiter : allAlgorithms(clock)) {
            clock.set(1900);
            String first = fire(limiter, "bob", 5);
            clock.set(2000);
            String second = fire(limiter, "bob", 5);
            int total = count(first) + count(second);
            System.out.printf("  %-16s t=1.9s %s  |  t=2.0s %s  -> %d allowed in 100ms%s%n",
                    limiter.name(), first, second, total,
                    total > LIMIT ? "   <-- LIMIT BREACHED" : "");
        }
        System.out.println();
        System.out.println("  Fixed window lets 2x through because the counter resets on a wall-clock");
        System.out.println("  boundary the client can see and aim at. The sliding variants do not have");
        System.out.println("  a boundary to aim at: at t=2.0s the previous window still counts in full.");

        section("3. Token bucket treats bursts as a feature, not a bug");
        clock.set(0);
        TokenBucket bucket = new TokenBucket(5, 1, clock); // holds 5, refills 1/second
        System.out.println("  capacity 5, refill 1/sec, bucket starts full");
        System.out.printf("  t=0s   fire 7  %s   tokens left %.1f%n",
                fire(bucket, "carol", 7), bucket.availableTokens("carol"));
        clock.advance(3000);
        System.out.printf("  t=3s   (idle)          tokens now  %.1f  (lazily refilled, no timer thread)%n",
                bucket.availableTokens("carol"));
        System.out.printf("  t=3s   fire 5  %s   tokens left %.1f%n",
                fire(bucket, "carol", 5), bucket.availableTokens("carol"));
        clock.advance(60_000);
        System.out.printf("  t=63s  (long idle)     tokens now  %.1f  (capped at capacity, not 60)%n",
                bucket.availableTokens("carol"));

        section("4. Sliding window log is exact - and you can watch the memory grow");
        clock.set(0);
        SlidingWindowLog log = new SlidingWindowLog(LIMIT, ONE_SECOND, clock);
        fire(log, "dave", 5);
        System.out.println("  after 5 requests, timestamps held: " + log.trackedTimestamps("dave"));
        System.out.println("  that is O(limit) per client. At 10,000/hour x 1M clients it does not fit,");
        System.out.println("  which is the entire reason the sliding window COUNTER exists.");
        clock.advance(1001);
        System.out.println("  t=1.001s fire 5  " + fire(log, "dave", 5) + "  (old timestamps pruned)");

        section("5. Limits are per client, never global");
        clock.set(0);
        RateLimiter shared = new SlidingWindowCounter(LIMIT, ONE_SECOND, clock);
        System.out.println("  alice fires 7   " + fire(shared, "alice", 7));
        System.out.println("  bob   fires 7   " + fire(shared, "bob", 7));
        System.out.println("  bob is unaffected by alice - the key of the map IS the isolation boundary.");
        System.out.println("  In production that key is user id, API key or IP, and choosing which one");
        System.out.println("  is a real design question: IP punishes everyone behind a corporate NAT.");

        section("6. Distributed follow-up (say this before they ask)");
        System.out.println("  Every counter above is a HashMap in one JVM. Across 50 servers each one");
        System.out.println("  enforces the full limit independently, so the real limit becomes 50x.");
        System.out.println("  Fix: move the counter to Redis and make check-and-increment atomic with a");
        System.out.println("  Lua script - GET then INCR from 50 machines is the same lost-update race");
        System.out.println("  that ConcurrentHashMap.compute() solves here on a single node.");

        System.out.println("\nDone.");
    }

    private static RateLimiter[] allAlgorithms(TimeSource clock) {
        return new RateLimiter[]{
                new FixedWindowCounter(LIMIT, ONE_SECOND, clock),
                new SlidingWindowLog(LIMIT, ONE_SECOND, clock),
                new SlidingWindowCounter(LIMIT, ONE_SECOND, clock),
                new TokenBucket(LIMIT, LIMIT, clock)
        };
    }

    /** Fires n requests and renders the verdicts as a string of dots and Xs. */
    private static String fire(RateLimiter limiter, String clientId, int n) {
        StringBuilder verdicts = new StringBuilder();
        for (int i = 0; i < n; i++) {
            verdicts.append(limiter.allow(clientId) ? '.' : 'X');
        }
        return verdicts.toString();
    }

    private static int count(String verdicts) {
        return (int) verdicts.chars().filter(c -> c == '.').count();
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    /** Time under test control. Every algorithm above reads the clock only through this. */
    static final class ManualClock implements TimeSource {

        private long millis;

        ManualClock(long millis) {
            this.millis = millis;
        }

        void set(long millis) {
            this.millis = millis;
        }

        void advance(long delta) {
            this.millis += delta;
        }

        @Override
        public long millis() {
            return millis;
        }
    }
}
