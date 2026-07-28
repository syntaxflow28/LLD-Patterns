package com.lld.problems.leaderboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * FACADE — the one class the rest of the application talks to.
 *
 * <p>Without it, every caller has to know that "Priya scored 300 in ranked mode" means <em>three</em>
 * separate boards must be updated, that the daily board's identity depends on today's UTC date, and
 * that boards are created lazily on first use. That knowledge would be duplicated into every call
 * site and would go stale the day someone adds a monthly window.
 *
 * <p><b>The fan-out is the point, and it is also the cost.</b> One gameplay event becomes N board
 * updates, so writes scale with the number of windows. That is the deliberate trade made in
 * {@link TimeWindow}: pay on the write path, which happens once per match, to make the read path —
 * which happens on every screen refresh for every player — a pre-sorted O(k) lookup.
 *
 * <p><b>Board creation is injected, not hard-coded.</b> The {@code rankIndexFactory} is what lets the
 * same service run with the O(n) index in a test and the Fenwick index in production, and it is what
 * makes the benchmark in the demo an honest comparison rather than two different programs.
 *
 * <p><b>Boards are keyed, not nested.</b> A flat {@code ConcurrentHashMap} keyed by
 * {@code game|window|bucket} beats a map-of-maps-of-maps: one atomic {@code computeIfAbsent} creates
 * a board race-free, whereas nested maps need a lock or a careful three-level dance to avoid two
 * threads each creating a board and one of them silently losing every score written to it.
 */
public final class LeaderboardService {

    private final Map<String, Leaderboard> boards = new ConcurrentHashMap<>();
    private final ScoringRule scoringRule;
    private final Supplier<RankIndex> rankIndexFactory;
    private final int trackedTopSize;
    private final List<LeaderboardListener> listeners = new ArrayList<>();

    public LeaderboardService(ScoringRule scoringRule, Supplier<RankIndex> rankIndexFactory, int trackedTopSize) {
        this.scoringRule = Objects.requireNonNull(scoringRule, "scoringRule");
        this.rankIndexFactory = Objects.requireNonNull(rankIndexFactory, "rankIndexFactory");
        this.trackedTopSize = trackedTopSize;
    }

    /** Listeners registered here are attached to every board the service creates from now on. */
    public void addListener(LeaderboardListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        boards.values().forEach(board -> board.addListener(listener));
    }

    /**
     * Records one gameplay result against every time window.
     *
     * <p>{@code at} is a parameter rather than {@code Instant.now()} for the usual reason: a test
     * that has to sleep until tomorrow to check the daily rollover is a test nobody runs twice.
     */
    public void submit(String game, String playerId, long points, Instant at) {
        for (TimeWindow window : TimeWindow.values()) {
            board(game, window, at).submit(playerId, points);
        }
    }

    /** Fetches (creating on first use) the board for this game, window and point in time. */
    public Leaderboard board(String game, TimeWindow window, Instant at) {
        String key = key(game, window, window.bucketKey(at));
        return boards.computeIfAbsent(key, boardKey -> {
            Leaderboard created = new Leaderboard(boardKey, scoringRule, rankIndexFactory.get(), trackedTopSize);
            listeners.forEach(created::addListener);
            return created;
        });
    }

    public List<RankedPlayer> topK(String game, TimeWindow window, Instant at, int k) {
        return board(game, window, at).topK(k);
    }

    /**
     * Drops daily boards older than the retention period.
     *
     * <p>This is the operational half of the design and it is where candidates run out of time. A
     * leaderboard service that never expires anything grows a new board every single day, forever,
     * and each one holds every player who played that day. Naming the cleanup — even without
     * implementing it — is worth a point; here it is one map removal per expired bucket because the
     * bucketing put every day's data behind its own key.
     *
     * @return how many boards were dropped
     */
    public int purgeDailyBoardsBefore(Instant now, int retentionDays) {
        LocalDate cutoff = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(retentionDays);
        List<String> expired = new ArrayList<>();
        for (String key : boards.keySet()) {
            String[] parts = key.split("\\|", 3);
            if (parts.length == 3 && TimeWindow.DAILY.name().equals(parts[1])
                    && LocalDate.parse(parts[2]).isBefore(cutoff)) {
                expired.add(key);
            }
        }
        expired.forEach(boards::remove);
        return expired.size();
    }

    public int boardCount() {
        return boards.size();
    }

    public List<String> boardNames() {
        List<String> names = new ArrayList<>(boards.keySet());
        names.sort(Comparator.naturalOrder());
        return names;
    }

    private static String key(String game, TimeWindow window, String bucket) {
        return game + "|" + window.name() + "|" + bucket;
    }
}
