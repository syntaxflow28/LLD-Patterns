package com.lld.problems.ratelimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TOKEN BUCKET — a bucket refills at a steady rate; each request spends one token.
 *
 * <p>The one algorithm here that treats bursts as a <em>feature</em>. A bucket that has been idle
 * fills to capacity, so a user who has done nothing for a minute can fire a burst immediately, and
 * is then throttled to the refill rate. That matches how humans and clients actually behave, which
 * is why AWS API Gateway, Stripe and most SDKs use it.
 *
 * <p><b>Two independent knobs, and interviewers will ask you to separate them:</b>
 * <ul>
 *   <li>{@code refillPerSecond} — the sustained throughput you are willing to serve.</li>
 *   <li>{@code capacity} — how much unused allowance can be banked, i.e. the largest burst.</li>
 * </ul>
 * Setting capacity equal to the refill rate gives you smooth behaviour with no burst; setting it to
 * ten times the rate lets a client bank ten seconds of idleness. The fact that these are separate
 * numbers is the entire advantage over the window-based algorithms.
 *
 * <p><b>No background thread.</b> The naive implementation schedules a timer to add tokens, which
 * means one thread per bucket, or one thread walking millions of buckets. Instead, refill is
 * computed lazily from elapsed time on the next request. Buckets nobody touches cost nothing.
 * "I refill lazily on read rather than with a scheduler" is a strong thing to say unprompted.
 *
 * <p><b>Leaky bucket</b> is the sibling algorithm: requests queue and drain at a constant rate,
 * smoothing output completely but adding latency and a queue that can overflow. Token bucket
 * shapes the input, leaky bucket shapes the output. Know the one-line difference.
 */
public class TokenBucket implements RateLimiter {

    private static final class Bucket {
        private double tokens;
        private long lastRefillMillis;

        private Bucket(double tokens, long lastRefillMillis) {
            this.tokens = tokens;
            this.lastRefillMillis = lastRefillMillis;
        }
    }

    private final double capacity;
    private final double refillPerSecond;
    private final TimeSource time;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucket(double capacity, double refillPerSecond, TimeSource time) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.time = time;
    }

    @Override
    public boolean allow(String clientId) {
        long now = time.millis();
        Bucket bucket = buckets.computeIfAbsent(clientId, key -> new Bucket(capacity, now));

        synchronized (bucket) {
            // Tokens are fractional on purpose: with 0.5 tokens/sec, integer maths would round the
            // refill to zero on every short interval and the bucket would never fill.
            double elapsedSeconds = (now - bucket.lastRefillMillis) / 1000.0;
            bucket.tokens = Math.min(capacity, bucket.tokens + elapsedSeconds * refillPerSecond);
            bucket.lastRefillMillis = now;

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    /** Exposed for the demo so the refill can be shown happening rather than asserted. */
    public double availableTokens(String clientId) {
        Bucket bucket = buckets.get(clientId);
        if (bucket == null) {
            return capacity;
        }
        synchronized (bucket) {
            double elapsedSeconds = (time.millis() - bucket.lastRefillMillis) / 1000.0;
            return Math.min(capacity, bucket.tokens + elapsedSeconds * refillPerSecond);
        }
    }

    @Override
    public String name() {
        return "TOKEN_BUCKET";
    }
}
