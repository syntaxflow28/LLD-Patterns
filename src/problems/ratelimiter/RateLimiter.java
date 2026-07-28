package problems.ratelimiter;

/**
 * STRATEGY — the four standard rate limiting algorithms behind one interface.
 *
 * <p>This question is asked constantly at SDE-2 and above, and it is almost never really about
 * design patterns. What is being measured is whether you know the algorithms, their memory cost,
 * and their failure modes:
 *
 * <table border="1">
 *   <caption>The four you should be able to compare cold</caption>
 *   <tr><th>Algorithm</th><th>Memory / client</th><th>Accuracy</th><th>Allows bursts?</th></tr>
 *   <tr><td>Fixed window counter</td><td>O(1)</td><td>Poor at boundaries</td><td>Yes, 2x at edges</td></tr>
 *   <tr><td>Sliding window log</td><td>O(limit)</td><td>Exact</td><td>No</td></tr>
 *   <tr><td>Sliding window counter</td><td>O(1)</td><td>Very good</td><td>Barely</td></tr>
 *   <tr><td>Token bucket</td><td>O(1)</td><td>Exact on average</td><td>Yes, deliberately</td></tr>
 * </table>
 *
 * <p><b>The answer that wins:</b> "Sliding window counter for API quotas — O(1) memory and no edge
 * burst. Token bucket when bursts are legitimate, like a user pasting a batch of requests. Sliding
 * window log only when the limit is small and exactness is contractual, because its memory grows
 * with the limit. Fixed window basically never, except when the counter must be trivially
 * explainable to a customer."
 *
 * <p><b>The follow-up that always comes:</b> "now make it work across 50 servers." The answer is
 * that the counter moves to Redis, and the read-modify-write becomes a Lua script so it stays
 * atomic — because {@code GET} then {@code INCR} from 50 machines is precisely the race this
 * interface hides on a single machine.
 */
public interface RateLimiter {

    /**
     * @return true if the request may proceed; false if it should be rejected with HTTP 429
     */
    boolean allow(String clientId);

    String name();
}
