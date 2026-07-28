package problems.leaderboard;

/**
 * STRATEGY — how do we answer "what rank am I?" without walking the whole board?
 *
 * <p><b>This is the interview.</b> Everything else in a leaderboard is straightforward; this one
 * question is where the design is won or lost, because the obvious data structure answers it in
 * O(n) and nobody notices until there are two million players.
 *
 * <p>The trap is specific and worth memorising: a {@code TreeSet} gives you O(log n) insert and O(k)
 * top-K, so it looks like it solves everything — and then you reach for
 * {@code ranked.headSet(me).size()} to get a rank. {@code size()} on a {@code SortedSet} <b>view</b>
 * is not cached; it counts the elements every time. That is O(n) per rank query on the single
 * hottest read path in the product ("show me my rank"), and it is the thing an interviewer is
 * waiting to see whether you notice.
 *
 * <p>Pulling rank out behind this interface makes the two answers swappable and lets you show both:
 * <ul>
 *   <li>{@link ScanRankIndex} — the correct, obvious, O(n) implementation you write first.</li>
 *   <li>{@link FenwickRankIndex} — a Binary Indexed Tree giving O(log maxScore), which is what you
 *       move to when asked to scale.</li>
 * </ul>
 *
 * <p><b>Why the index tracks scores, not players.</b> Rank only depends on <em>how many players are
 * above you</em>, which is a function of scores alone. Keeping player identity out of the index
 * halves its memory and lets the Fenwick implementation be a flat array of counters.
 *
 * <p><b>Ranking semantics — ask about this.</b> With scores 100, 90, 90, 80 there are two accepted
 * answers and they are both "correct":
 * <ul>
 *   <li><b>Competition rank</b> (what sport uses): 1, 2, 2, <b>4</b> — the tied pair consumes both
 *       slots. Supported by {@link #countStrictlyGreater}.</li>
 *   <li><b>Dense rank</b> (what game UIs usually use): 1, 2, 2, <b>3</b> — no gaps. Supported by
 *       {@link #countDistinctScoresStrictlyGreater}.</li>
 * </ul>
 * Naming this distinction unprompted is a cheap, high-signal point.
 */
public interface RankIndex {

    /** A player now holds this score. */
    void add(long score);

    /** A player no longer holds this score (they left, or their score changed). */
    void remove(long score);

    /** @return how many players have a strictly higher score; competition rank is this + 1 */
    long countStrictlyGreater(long score);

    /** @return how many <em>distinct</em> scores are strictly higher; dense rank is this + 1 */
    long countDistinctScoresStrictlyGreater(long score);

    void clear();

    String name();
}
