package problems.ratelimiter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SLIDING WINDOW LOG — remember the timestamp of every request.
 *
 * <p>Exact by construction: to decide a request, drop every timestamp older than one window and
 * count what is left. There is no boundary effect because there is no boundary — the window moves
 * with the request.
 *
 * <p><b>The cost, which is the point of the algorithm.</b> Memory is O(limit) <em>per client</em>.
 * At 10,000 requests/hour per client across a million clients that is ten billion timestamps, and
 * the design falls over. This is the algorithm you name to prove you understand exactness, and then
 * reject on memory grounds — which is exactly the reasoning the sliding window counter exists to
 * short-circuit.
 *
 * <p><b>Why a {@link Deque}.</b> Timestamps arrive in order, expire in order, and are only ever
 * examined at the two ends: prune from the head, append at the tail. Both O(1). A {@code List} with
 * {@code remove(0)} would be O(n) per prune.
 *
 * <p><b>Why {@code synchronized} on the deque rather than a {@code ConcurrentLinkedDeque}.</b>
 * Prune, count and append have to be one atomic decision. A concurrent deque makes each step atomic
 * but lets two threads both observe "there is room" and both append, admitting limit+1 requests.
 * Same lesson as the cache: the compound operation is what needs the lock, not the container.
 */
public class SlidingWindowLog implements RateLimiter {

    private final int limit;
    private final long windowMillis;
    private final TimeSource time;
    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    public SlidingWindowLog(int limit, long windowMillis, TimeSource time) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.time = time;
    }

    @Override
    public boolean allow(String clientId) {
        long now = time.millis();
        Deque<Long> timestamps = requestLog.computeIfAbsent(clientId, key -> new ArrayDeque<>());

        synchronized (timestamps) {
            long cutoff = now - windowMillis;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() < limit) {
                timestamps.addLast(now);
                return true;
            }
            return false;
        }
    }

    /** Exposed so the demo can show the memory cost rather than just claiming it. */
    public int trackedTimestamps(String clientId) {
        Deque<Long> timestamps = requestLog.get(clientId);
        if (timestamps == null) {
            return 0;
        }
        synchronized (timestamps) {
            return timestamps.size();
        }
    }

    @Override
    public String name() {
        return "SLIDING_LOG";
    }
}
