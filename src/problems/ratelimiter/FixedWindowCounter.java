package problems.ratelimiter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIXED WINDOW COUNTER — one counter per client, reset every window.
 *
 * <p>The simplest algorithm, and the one with the famous defect. Windows are aligned to the epoch
 * (0-1s, 1-2s, ...), so a client can spend its entire quota at the very end of one window and its
 * entire quota at the very start of the next, and push <b>2x the limit through in a fraction of a
 * second</b>. With a limit of 100/minute you can serve 200 requests between 11:59:59 and 12:00:01.
 *
 * <p>{@code RateLimiterDemo} reproduces this exactly rather than asserting it. Being able to
 * describe this failure — and then reach for the sliding window counter as the O(1) fix — is the
 * whole point of knowing this algorithm.
 *
 * <p><b>Concurrency note.</b> {@link ConcurrentHashMap#compute} applies its function atomically
 * while holding the bin lock for that key, so check-and-increment cannot interleave for a single
 * client. A plain {@code get}/{@code put} pair here would lose increments under load and silently
 * let clients over-spend. The {@code boolean[]} holder is a side effect inside that function, which
 * is normally discouraged — it is safe here precisely because the function runs under the lock and
 * is applied exactly once.
 */
public class FixedWindowCounter implements RateLimiter {

    private record Window(long startMillis, int count) {
    }

    private final int limit;
    private final long windowMillis;
    private final TimeSource time;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowCounter(int limit, long windowMillis, TimeSource time) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.time = time;
    }

    @Override
    public boolean allow(String clientId) {
        long now = time.millis();
        long currentWindowStart = now - (now % windowMillis); // aligned to the epoch
        boolean[] allowed = new boolean[1];

        windows.compute(clientId, (key, existing) -> {
            if (existing == null || existing.startMillis() != currentWindowStart) {
                allowed[0] = true;
                return new Window(currentWindowStart, 1); // fresh window, counter resets to zero
            }
            if (existing.count() < limit) {
                allowed[0] = true;
                return new Window(existing.startMillis(), existing.count() + 1);
            }
            allowed[0] = false;
            return existing; // rejected requests are not counted, so a spammer cannot extend their own ban
        });

        return allowed[0];
    }

    @Override
    public String name() {
        return "FIXED_WINDOW";
    }
}
