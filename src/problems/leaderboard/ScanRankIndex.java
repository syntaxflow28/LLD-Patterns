package problems.leaderboard;

import java.util.HashMap;
import java.util.Map;

/**
 * The honest first implementation: count how many scores are above you, one at a time.
 *
 * <p><b>Write this one first in the interview.</b> It is correct, it is five lines, and it lets you
 * finish the rest of the design before optimising. Announcing "rank is O(n) here, I'll come back to
 * it" is a much stronger move than silently shipping an O(n) hot path or burning fifteen minutes on
 * a Fenwick tree before the API exists.
 *
 * <p><b>Complexity.</b> {@code add} and {@code remove} are O(1). {@code countStrictlyGreater} is
 * O(distinct scores), which degenerates to O(n) exactly when scores are spread out — which is
 * always, because that is what scores do. At a million players this is roughly a million
 * comparisons per "what's my rank?" request, and that request fires on every screen refresh for
 * every player.
 *
 * <p><b>Why a count map rather than a list of scores.</b> Storing every player's score in a list
 * would make {@code remove} O(n) too, which muddies the picture: it is only the <em>rank query</em>
 * that is slow here, and the map keeps that clear.
 */
public final class ScanRankIndex implements RankIndex {

    /** score to number of players currently holding it. */
    private final Map<Long, Integer> playersPerScore = new HashMap<>();

    private long totalPlayers;

    @Override
    public void add(long score) {
        playersPerScore.merge(score, 1, Integer::sum);
        totalPlayers++;
    }

    @Override
    public void remove(long score) {
        // Note the merge-to-null idiom: returning null from merge REMOVES the key, which keeps the
        // map free of zero-count entries. Leaving them behind would make the scan below slower over
        // time even as players churn - a slow leak that only shows up under load.
        playersPerScore.merge(score, -1, (current, delta) -> current + delta == 0 ? null : current + delta);
        totalPlayers--;
    }

    @Override
    public long countStrictlyGreater(long score) {
        long above = 0;
        for (Map.Entry<Long, Integer> bucket : playersPerScore.entrySet()) {
            if (bucket.getKey() > score) {
                above += bucket.getValue();
            }
        }
        return above;
    }

    @Override
    public long countDistinctScoresStrictlyGreater(long score) {
        long above = 0;
        for (Long bucketScore : playersPerScore.keySet()) {
            if (bucketScore > score) {
                above++;
            }
        }
        return above;
    }

    @Override
    public void clear() {
        playersPerScore.clear();
        totalPlayers = 0;
    }

    @Override
    public String name() {
        return "scan O(n)";
    }

    long totalPlayers() {
        return totalPlayers;
    }
}
