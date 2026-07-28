package com.lld.problems.leaderboard;

/**
 * The answer to "now make rank O(log n)": a Binary Indexed Tree (Fenwick tree) over score values.
 *
 * <p><b>The key insight, and the sentence to say out loud:</b> ranking does not need the players
 * sorted — it only needs to count how many players sit above a score. Counting a prefix of an array
 * while that array is being updated is precisely what a Fenwick tree does, in O(log range) for both
 * the update and the count.
 *
 * <p><b>How it works in one breath.</b> Index the array by score. {@code playerCounts[s]} is how
 * many players hold score {@code s}. The tree stores overlapping partial sums so that any prefix
 * "how many players scored at most X" is the sum of about log(range) cells instead of X of them.
 * Players strictly above a score is then {@code total - prefix(score)}.
 *
 * <p><b>Two trees, because there are two ranking questions.</b> {@code playerCounts} counts players
 * and answers competition rank (1, 2, 2, 4). {@code distinctScoreCounts} counts <em>occupied score
 * buckets</em> and answers dense rank (1, 2, 2, 3). The second tree is only touched when a bucket
 * transitions between empty and non-empty, which the {@code occupancy} array detects — that is the
 * whole reason {@code occupancy} exists alongside the trees.
 *
 * <p><b>The trade-off you must volunteer.</b> Memory here is proportional to the score
 * <em>range</em>, not the player count. That is a bargain for a game capped at 100,000 points and
 * ruinous for scores that run to billions or are floating point. The fixes, in the order an
 * interviewer wants to hear them:
 * <ol>
 *   <li><b>Bucket the scores.</b> Nobody needs to distinguish rank at single-point granularity below
 *       the top of the board; round to the nearest 10 or 100 and the range collapses.</li>
 *   <li><b>Coordinate-compress</b> to the distinct scores actually present, then the range is
 *       bounded by the player count.</li>
 *   <li><b>Use a skip list</b> — which is exactly what Redis sorted sets do, giving O(log n)
 *       {@code ZREVRANK} with no dependence on the score range at all. In production this class
 *       <em>is</em> a Redis ZSET; implementing it by hand is the interview exercise.</li>
 * </ol>
 *
 * <p>Scores must be non-negative and within {@code maxScore}. Negative scores would need a fixed
 * offset added on the way in — worth mentioning, not worth coding.
 */
public final class FenwickRankIndex implements RankIndex {

    private final int maxScore;

    /** 1-indexed BIT over players; tree index i covers score value i - 1. */
    private final long[] playerCounts;

    /** 1-indexed BIT over occupied score buckets, for dense rank. */
    private final long[] distinctScoreCounts;

    /** Raw per-score population, used only to detect the 0 to 1 and 1 to 0 transitions. */
    private final int[] occupancy;

    private long totalPlayers;
    private long totalDistinctScores;

    public FenwickRankIndex(int maxScore) {
        if (maxScore < 0) {
            throw new IllegalArgumentException("maxScore must be non-negative");
        }
        this.maxScore = maxScore;
        this.playerCounts = new long[maxScore + 2];
        this.distinctScoreCounts = new long[maxScore + 2];
        this.occupancy = new int[maxScore + 1];
    }

    @Override
    public void add(long score) {
        int bucket = checkedBucket(score);
        update(playerCounts, bucket + 1, 1);
        totalPlayers++;
        if (occupancy[bucket]++ == 0) {
            update(distinctScoreCounts, bucket + 1, 1);
            totalDistinctScores++;
        }
    }

    @Override
    public void remove(long score) {
        int bucket = checkedBucket(score);
        if (occupancy[bucket] == 0) {
            throw new IllegalStateException("no player is holding score " + score);
        }
        update(playerCounts, bucket + 1, -1);
        totalPlayers--;
        if (--occupancy[bucket] == 0) {
            update(distinctScoreCounts, bucket + 1, -1);
            totalDistinctScores--;
        }
    }

    @Override
    public long countStrictlyGreater(long score) {
        if (score >= maxScore) {
            return 0;
        }
        int bucket = checkedBucket(Math.max(score, 0));
        // prefix(bucket + 1) counts every player scoring at most `score`, so the remainder is
        // exactly the players above. No iteration over players anywhere.
        return totalPlayers - prefix(playerCounts, bucket + 1);
    }

    @Override
    public long countDistinctScoresStrictlyGreater(long score) {
        if (score >= maxScore) {
            return 0;
        }
        int bucket = checkedBucket(Math.max(score, 0));
        return totalDistinctScores - prefix(distinctScoreCounts, bucket + 1);
    }

    @Override
    public void clear() {
        java.util.Arrays.fill(playerCounts, 0L);
        java.util.Arrays.fill(distinctScoreCounts, 0L);
        java.util.Arrays.fill(occupancy, 0);
        totalPlayers = 0;
        totalDistinctScores = 0;
    }

    @Override
    public String name() {
        return "fenwick O(log range)";
    }

    /** Adds {@code delta} at position {@code i}, then at every ancestor cell that covers it. */
    private static void update(long[] tree, int i, int delta) {
        for (int cursor = i; cursor < tree.length; cursor += cursor & -cursor) {
            tree[cursor] += delta;
        }
    }

    /** @return the sum of positions 1..i, i.e. the number of players scoring at most {@code i - 1} */
    private static long prefix(long[] tree, int i) {
        long sum = 0;
        for (int cursor = i; cursor > 0; cursor -= cursor & -cursor) {
            sum += tree[cursor];
        }
        return sum;
    }

    private int checkedBucket(long score) {
        if (score < 0 || score > maxScore) {
            throw new IllegalArgumentException(
                    "score " + score + " is outside the indexed range [0, " + maxScore + "]");
        }
        return (int) score;
    }
}
