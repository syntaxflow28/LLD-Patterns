package problems.notification;

/**
 * Exponential backoff with a cap.
 *
 * <p><b>Why retries need a policy object rather than a {@code for} loop with a magic number.</b>
 * Retry behaviour differs per channel and per deployment: an SMS gateway that charges per attempt
 * wants two tries, an internal push service can afford five. Making it a value means it can be
 * configured, tested, and reasoned about on its own.
 *
 * <p><b>Why exponential and not fixed.</b> Fixed-interval retries from many clients synchronise into
 * a thundering herd and keep a struggling service down. Doubling the wait gives the downstream room
 * to recover — the difference between a blip and an outage.
 *
 * <p><b>What is deliberately missing, and worth volunteering:</b>
 * <ul>
 *   <li><b>Jitter.</b> Even with exponential backoff, a thousand clients that failed at the same
 *       instant retry at the same instant. Randomising each delay is a one-line fix that production
 *       systems always have and interview answers usually forget.</li>
 *   <li><b>A circuit breaker.</b> Retrying is right for a transient blip and wrong for a dead
 *       provider — at that point retries just multiply load. After N consecutive failures the
 *       breaker should open and fail fast.</li>
 *   <li><b>A dead-letter queue.</b> Once attempts are exhausted the message should land somewhere
 *       replayable, not vanish into a log line.</li>
 * </ul>
 * Naming those three unprompted is what makes this answer sound like production experience rather
 * than a textbook.
 */
public record RetryPolicy(int maxAttempts, long initialBackoffMillis, double multiplier, long maxBackoffMillis) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be at least 1.0");
        }
    }

    /** Sensible default: three tries, 100ms then 200ms. */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, 100, 2.0, 5_000);
    }

    /** For channels where a retry costs money, or where the caller is waiting. */
    public static RetryPolicy noRetry() {
        return new RetryPolicy(1, 0, 1.0, 0);
    }

    public boolean shouldRetry(int attemptsMade) {
        return attemptsMade < maxAttempts;
    }

    /** @param attemptsMade how many attempts have already failed (1 after the first failure) */
    public long backoffMillis(int attemptsMade) {
        double delay = initialBackoffMillis * Math.pow(multiplier, attemptsMade - 1.0);
        return (long) Math.min(delay, maxBackoffMillis);
    }
}
