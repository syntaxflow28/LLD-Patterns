package problems.leaderboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A single leaderboard: one game mode, one time window, every player in it.
 *
 * <p><b>The structure, and why nothing simpler works.</b> Three requirements pull in three different
 * directions, and every naive answer satisfies two of them:
 *
 * <table border="1">
 *   <caption>Candidate structures</caption>
 *   <tr><th>Structure</th><th>submit</th><th>top K</th><th>rank of player</th></tr>
 *   <tr><td>{@code HashMap} alone</td><td>O(1)</td><td>O(n log n) — sort everything</td><td>O(n)</td></tr>
 *   <tr><td>Sorted {@code ArrayList}</td><td>O(n) — shift on insert</td><td>O(k)</td><td>O(log n)</td></tr>
 *   <tr><td>Max-heap</td><td>O(log n)</td><td>O(k log n) destructive</td><td>O(n)</td></tr>
 *   <tr><td><b>HashMap + TreeSet + rank index</b></td><td>O(log n)</td><td>O(k)</td><td>O(log range)</td></tr>
 * </table>
 *
 * <p>So the answer is three cooperating structures, and each earns its place:
 * <ul>
 *   <li>{@code byPlayer} — a {@code HashMap} giving O(1) "what is this player's current entry?",
 *       which is needed on <em>every</em> submission to find the old entry before replacing it.
 *       Without it you would scan the tree to find the player, and the whole thing collapses to
 *       O(n).</li>
 *   <li>{@code ranked} — a {@code TreeSet} keeping everyone in rank order, giving O(k) top-K and
 *       O(log n + k) "show my neighbours".</li>
 *   <li>{@code rankIndex} — see {@link RankIndex}; the TreeSet cannot answer "what number am I?"
 *       faster than O(n), and that is the hottest query on the board.</li>
 * </ul>
 *
 * <p><b>The invariant that keeps them consistent:</b> every mutation touches all three, under one
 * write lock, and always in the order remove-old then add-new. Two of the three would silently drift
 * otherwise, and drift in a rank index is invisible until a user complains that their rank is wrong.
 *
 * <p><b>Why a ReadWriteLock rather than {@code synchronized}.</b> Leaderboards are read-dominated by
 * a wide margin — every player refreshing a screen is a read, only actual gameplay is a write. A
 * plain {@code synchronized} serialises the reads against each other for no reason.
 * {@code ReentrantReadWriteLock} lets every concurrent "show me the top 100" proceed in parallel and
 * only excludes them during the brief write. Say <em>why</em> you picked it; "reads outnumber writes
 * by orders of magnitude here" is the justification, and if that were not true a plain lock would be
 * the better choice because it is cheaper.
 */
public final class Leaderboard {

    private final String name;
    private final ScoringRule scoringRule;
    private final RankIndex rankIndex;
    private final int trackedTopSize;

    private final Map<String, Entry> byPlayer = new HashMap<>();
    private final NavigableSet<Entry> ranked = new TreeSet<>(Entry.RANK_ORDER);

    // CopyOnWriteArrayList: listeners are registered once at startup and iterated on every
    // submission, which is the exact read-heavy/write-never shape it is built for.
    private final List<LeaderboardListener> listeners = new CopyOnWriteArrayList<>();

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private long sequence;
    private Set<String> trackedTop = Set.of();

    public Leaderboard(String name, ScoringRule scoringRule, RankIndex rankIndex, int trackedTopSize) {
        this.name = Objects.requireNonNull(name, "name");
        this.scoringRule = Objects.requireNonNull(scoringRule, "scoringRule");
        this.rankIndex = Objects.requireNonNull(rankIndex, "rankIndex");
        if (trackedTopSize < 0) {
            throw new IllegalArgumentException("trackedTopSize must be non-negative");
        }
        this.trackedTopSize = trackedTopSize;
    }

    public void addListener(LeaderboardListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /**
     * Records a submission and returns the player's new score.
     *
     * <p>The remove-then-insert dance is the heart of the class. {@code Entry} is immutable
     * precisely so that this is the only way to update a score: mutating the entry in place would
     * leave it sitting in the {@code TreeSet} at a position computed from its old score, at which
     * point the tree is corrupt and {@code remove()} can never find it again.
     */
    public long submit(String playerId, long points) {
        Objects.requireNonNull(playerId, "playerId");

        long oldScore;
        long newScore;
        Set<String> topBefore;
        Set<String> topAfter;

        lock.writeLock().lock();
        try {
            Entry existing = byPlayer.get(playerId);
            oldScore = existing == null ? 0L : existing.score();
            newScore = scoringRule.combine(oldScore, points);

            if (existing != null) {
                // Remove BEFORE the score changes, while the tree can still locate the entry.
                ranked.remove(existing);
                rankIndex.remove(oldScore);
            }

            // A fresh sequence stamps "reached this score now", which is what makes the tie-break
            // rule "whoever got here first ranks higher" actually mean something.
            Entry updated = new Entry(playerId, newScore, sequence++);
            ranked.add(updated);
            byPlayer.put(playerId, updated);
            rankIndex.add(newScore);

            topBefore = trackedTop;
            topAfter = snapshotTrackedTop();
            trackedTop = topAfter;
        } finally {
            lock.writeLock().unlock();
        }

        // Deliberately outside the lock: a listener that calls a push provider must not be able to
        // block every other score submission in the process.
        publish(playerId, oldScore, newScore, topBefore, topAfter);
        return newScore;
    }

    /**
     * The board's front page. O(k) — it walks the first k entries of an already-ordered structure
     * and never touches the other million.
     *
     * <p>Ranks here use <b>competition</b> numbering (100, 90, 90, 80 gives 1, 2, 2, 4) because that
     * is what a scoreboard shows. {@link #denseRankOf} gives the no-gaps variant.
     */
    public List<RankedPlayer> topK(int k) {
        if (k <= 0) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            List<RankedPlayer> page = new ArrayList<>(Math.min(k, ranked.size()));
            long position = 0;
            long rank = 0;
            long previousScore = Long.MIN_VALUE;
            for (Entry entry : ranked) {
                position++;
                if (entry.score() != previousScore) {
                    rank = position;
                    previousScore = entry.score();
                }
                page.add(new RankedPlayer(rank, entry.playerId(), entry.score()));
                if (page.size() == k) {
                    break;
                }
            }
            return page;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Competition rank: ties share a number and consume the slots after it (1, 2, 2, 4). */
    public OptionalLong rankOf(String playerId) {
        lock.readLock().lock();
        try {
            Entry entry = byPlayer.get(playerId);
            return entry == null
                    ? OptionalLong.empty()
                    : OptionalLong.of(rankIndex.countStrictlyGreater(entry.score()) + 1);
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Dense rank: ties share a number and the next score follows immediately (1, 2, 2, 3). */
    public OptionalLong denseRankOf(String playerId) {
        lock.readLock().lock();
        try {
            Entry entry = byPlayer.get(playerId);
            return entry == null
                    ? OptionalLong.empty()
                    : OptionalLong.of(rankIndex.countDistinctScoresStrictlyGreater(entry.score()) + 1);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * The "you are here" screen — a player and their nearest neighbours in both directions.
     *
     * <p>Almost always asked as a follow-up, and it is the query that justifies the {@code TreeSet}
     * outright. A rank index alone cannot answer it: knowing you are number 4,912 does not tell you
     * <em>who</em> 4,911 is. The navigable set does it in O(log n + radius) with
     * {@code headSet(...).descendingIterator()} walking upward and {@code tailSet(...)} walking down.
     */
    public List<RankedPlayer> around(String playerId, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("radius must be non-negative");
        }
        lock.readLock().lock();
        try {
            Entry me = byPlayer.get(playerId);
            if (me == null) {
                return List.of();
            }

            List<Entry> above = new ArrayList<>(radius);
            Iterator<Entry> upward = ranked.headSet(me, false).descendingIterator();
            while (upward.hasNext() && above.size() < radius) {
                above.add(upward.next());
            }
            Collections.reverse(above); // collected nearest-first, displayed best-first

            List<Entry> window = new ArrayList<>(above);
            window.add(me);

            Iterator<Entry> downward = ranked.tailSet(me, false).iterator();
            int below = 0;
            while (downward.hasNext() && below < radius) {
                window.add(downward.next());
                below++;
            }

            List<RankedPlayer> result = new ArrayList<>(window.size());
            for (Entry entry : window) {
                result.add(new RankedPlayer(
                        rankIndex.countStrictlyGreater(entry.score()) + 1, entry.playerId(), entry.score()));
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public OptionalLong scoreOf(String playerId) {
        lock.readLock().lock();
        try {
            Entry entry = byPlayer.get(playerId);
            return entry == null ? OptionalLong.empty() : OptionalLong.of(entry.score());
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Removes a player entirely — bans, GDPR deletion, account closure. All three structures. */
    public boolean remove(String playerId) {
        lock.writeLock().lock();
        try {
            Entry existing = byPlayer.remove(playerId);
            if (existing == null) {
                return false;
            }
            ranked.remove(existing);
            rankIndex.remove(existing.score());
            trackedTop = snapshotTrackedTop();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return byPlayer.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public String name() {
        return name;
    }

    public ScoringRule scoringRule() {
        return scoringRule;
    }

    public String rankIndexName() {
        return rankIndex.name();
    }

    /** Called under the write lock; O(trackedTopSize), which is why tracking the top is cheap. */
    private Set<String> snapshotTrackedTop() {
        if (trackedTopSize == 0) {
            return Set.of();
        }
        Set<String> top = new LinkedHashSet<>();
        for (Entry entry : ranked) {
            if (top.size() == trackedTopSize) {
                break;
            }
            top.add(entry.playerId());
        }
        return top;
    }

    private void publish(String playerId, long oldScore, long newScore, Set<String> before, Set<String> after) {
        for (LeaderboardListener listener : listeners) {
            // One try/catch per listener. A broken metrics exporter must not stop the push notifier
            // that was registered after it, and neither may fail the score submission itself.
            try {
                listener.onScoreUpdated(name, playerId, oldScore, newScore);

                long position = 0;
                for (String top : after) {
                    position++;
                    if (!before.contains(top)) {
                        listener.onEnteredTop(name, top, position);
                    }
                }
                for (String previous : before) {
                    if (!after.contains(previous)) {
                        listener.onDisplacedFromTop(name, previous);
                    }
                }
            } catch (RuntimeException listenerFailure) {
                System.out.println("      [listener " + listener.getClass().getSimpleName()
                        + " failed, ignored] " + listenerFailure.getMessage());
            }
        }
    }
}
