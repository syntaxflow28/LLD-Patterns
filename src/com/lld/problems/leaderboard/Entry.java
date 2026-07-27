package com.lld.problems.leaderboard;

import java.util.Comparator;

/**
 * One player's current standing — deliberately IMMUTABLE, and that is a design decision, not a
 * stylistic one.
 *
 * <p><b>Why immutable.</b> These entries live inside a {@link java.util.TreeSet}. A {@code TreeSet}
 * decides where an element belongs at the moment you insert it and never revisits that decision. If
 * you mutate {@code score} while the entry is in the set, the tree is now sorted by a value that no
 * longer exists: {@code contains()} returns false for an element that is physically present,
 * {@code remove()} silently fails, and the entry becomes an unreachable leak. Making the record
 * immutable means the only legal update is <em>remove, build a new entry, re-insert</em> — which is
 * exactly what {@link Leaderboard#submit} does.
 *
 * <p><b>Why {@code sequence} exists.</b> It is a monotonic counter stamped at the moment this player
 * reached this score, and it implements the tie-break rule every real leaderboard uses: <em>if two
 * players have the same score, whoever got there first ranks higher.</em> Without it, ties would be
 * ordered arbitrarily and a player's displayed rank would flicker between page loads for no visible
 * reason.
 *
 * @param playerId the player this entry belongs to
 * @param score    the player's current score under the board's {@link ScoringRule}
 * @param sequence a monotonic stamp of when this score was reached; lower means earlier
 */
public record Entry(String playerId, long score, long sequence) {

    /**
     * The ordering the whole design rests on: best player first.
     *
     * <p><b>The bug this comparator exists to prevent</b> is the single most common mistake in this
     * problem. The obvious comparator is "compare by score, descending" — and it silently loses
     * players. {@code TreeSet} treats "compare returns 0" as "these are the same element", so the
     * second player to score 500 is not added at all. Your leaderboard shows 3 of 5 players and
     * nothing throws.
     *
     * <p>The fix is to make the comparator a <b>total order consistent with equals</b>: keep adding
     * tie-breakers until it is impossible for two <em>different</em> players to compare equal. Here
     * that is score (desc) → sequence (asc, earliest wins) → playerId (asc, purely to guarantee
     * uniqueness — two entries can never share a player id, so this branch is the safety net that
     * makes the guarantee airtight rather than a rule that fires often).
     *
     * <p>Say this out loud in the interview. Interviewers watch for whether your comparator can
     * return 0 for distinct elements, because that single line decides whether the data structure
     * works at all.
     */
    public static final Comparator<Entry> RANK_ORDER =
            Comparator.<Entry>comparingLong(Entry::score).reversed()
                    .thenComparingLong(Entry::sequence)
                    .thenComparing(Entry::playerId);

    public Entry {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId is required");
        }
    }
}
