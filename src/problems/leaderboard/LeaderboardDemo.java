package problems.leaderboard;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * GAMING LEADERBOARD — a complete, runnable design.
 *
 * <p><b>The question, as it is actually asked:</b> "Design a leaderboard for an online game.
 * Millions of players, scores update in real time, players need to see the top 10 and their own
 * rank. Support daily, weekly and all-time boards."
 *
 * <p><b>Why this problem is a favourite.</b> It looks like a data-structure exercise and is really a
 * design exercise, but it will not let you fake either half. There is a genuine algorithmic core
 * (top-K, insert and rank are three operations that no single structure does well), a genuine
 * modelling core (scoring rules, time windows, ranking semantics), and a genuine operational core
 * (concurrency, expiry, scale). Candidates who only do patterns write an elegant O(n) rank query;
 * candidates who only do algorithms write a Fenwick tree with no API around it.
 *
 * <p><b>Patterns combined here, and the axis each one isolates:</b>
 * <ul>
 *   <li>{@link ScoringRule} — <b>Strategy</b>: accumulate vs personal-best vs latest.</li>
 *   <li>{@link RankIndex} — <b>Strategy</b>: the O(n) implementation you write first, and the
 *       O(log range) one you move to. This is the axis the interviewer will push on.</li>
 *   <li>{@link TimeWindow} — <b>Strategy as an enum</b>: daily/weekly/all-time as pre-aggregated
 *       buckets rather than a filter over history.</li>
 *   <li>{@link LeaderboardListener} — <b>Observer</b>: "you broke into the top 10" without the board
 *       knowing what a push notification is.</li>
 *   <li>{@link LeaderboardService} — <b>Facade</b>: one submission fans out to every window.</li>
 *   <li>{@link RankedPlayer} — <b>DTO</b>: keeps the tie-break {@code sequence} out of the wire
 *       contract so the tie-break rule can change without breaking clients.</li>
 * </ul>
 *
 * <p>Every section below prints real output, including the two failure modes that are usually only
 * described: a comparator that silently loses players, and a mutation that corrupts a
 * {@code TreeSet} in place.
 */
public class LeaderboardDemo {

    public static void main(String[] args) throws Exception {
        section("1. Why no single data structure works");
        System.out.println("  Three operations pull in three directions:");
        System.out.println();
        System.out.println("    structure           submit        top K            rank of player");
        System.out.println("    ----------------------------------------------------------------");
        System.out.println("    HashMap only        O(1)          O(n log n)       O(n)");
        System.out.println("    sorted ArrayList    O(n)          O(k)             O(log n)");
        System.out.println("    max-heap            O(log n)      O(k log n)       O(n)");
        System.out.println("    HashMap + TreeSet   O(log n)      O(k)             O(n)  <-- still!");
        System.out.println("    ... + rank index    O(log n)      O(k)             O(log range)");
        System.out.println();
        System.out.println("  The fourth row is where most candidates stop, and it is the trap: a TreeSet");
        System.out.println("  looks like it solves everything until you ask it for a rank number. It cannot,");
        System.out.println("  so this design carries a third structure whose only job is counting.");

        section("2. A board in use");
        Leaderboard ranked = newBoard("ranked", ScoringRule.BEST, 3);
        ranked.submit("priya", 980);
        ranked.submit("rahul", 940);
        ranked.submit("meera", 940);
        ranked.submit("sam", 870);
        ranked.submit("dev", 720);
        ranked.submit("aisha", 640);
        print(ranked.topK(5));
        System.out.println("  rahul and meera both scored 940 - rahul is ahead because he got there first.");
        System.out.println("  That tie-break is not cosmetic; without it the two would swap places between");
        System.out.println("  page loads and players would report it as a bug.");

        section("3. THE comparator bug, reproduced live");
        System.out.println("  The obvious comparator is 'by score, descending'. Watch what it does:");
        TreeSet<Entry> broken = new TreeSet<>(Comparator.<Entry>comparingLong(Entry::score).reversed());
        List<Entry> sixPlayers = List.of(
                new Entry("priya", 980, 1),
                new Entry("rahul", 940, 2),
                new Entry("meera", 940, 3),
                new Entry("sam", 870, 4),
                new Entry("dev", 870, 5),
                new Entry("aisha", 640, 6));
        sixPlayers.forEach(broken::add);
        System.out.println("      inserted : " + sixPlayers.size() + " players");
        System.out.println("      set size : " + broken.size() + "   <-- players silently vanished");
        System.out.println("      present  : " + names(broken));
        System.out.println();
        System.out.println("  TreeSet reads 'compare returned 0' as 'this element is already here', so the");
        System.out.println("  SECOND player on 940 is never added. Nothing throws. add() even returns false");
        System.out.println("  and nobody checks it.");

        TreeSet<Entry> correct = new TreeSet<>(Entry.RANK_ORDER);
        sixPlayers.forEach(correct::add);
        System.out.println();
        System.out.println("  With a total order (score -> sequence -> playerId):");
        System.out.println("      set size : " + correct.size());
        System.out.println("      present  : " + names(correct));
        System.out.println("  Rule: keep adding tie-breakers until two DIFFERENT players can never compare");
        System.out.println("  equal. Interviewers watch for exactly this line.");

        section("4. Mutating an entry in place corrupts the tree");
        System.out.println("  This is why Entry is an immutable record rather than a bean with a setter.");
        TreeSet<MutableEntry> fragile = new TreeSet<>(
                Comparator.<MutableEntry>comparingLong(e -> e.score).reversed().thenComparing(e -> e.playerId));
        MutableEntry victim = new MutableEntry("aisha", 640);
        for (long score : new long[] {980, 940, 900, 870, 820, 780, 720}) {
            fragile.add(new MutableEntry("p" + score, score));
        }
        fragile.add(victim);
        System.out.println("      before  : contains(aisha) = " + fragile.contains(victim));
        System.out.println("                " + fragile);

        victim.score = 1200; // the "obvious" way to update a score
        System.out.println("      mutated aisha 640 -> 1200 while she sits in the set");
        System.out.println("      after   : contains(aisha) = " + fragile.contains(victim)
                + ", remove(aisha) = " + fragile.remove(victim) + ", size = " + fragile.size());
        System.out.println("                " + fragile);
        System.out.println("  The top scorer is displayed LAST, and the set can no longer find her at all.");
        System.out.println("  The tree filed her under 640 and never revisits that decision, so the binary");
        System.out.println("  search for 1200 walks off toward the high scores and misses the node entirely.");
        System.out.println("  She is now unreachable, unremovable and sorted wrong - and nothing threw.");
        System.out.println("  The only safe update is remove -> build a new entry -> re-insert, which is");
        System.out.println("  exactly what submit() does, and immutability is what forces callers into it.");

        section("5. Two correct answers to 'what rank am I?'");
        System.out.println("  scores: priya 980, rahul 940, meera 940, sam 870, dev 720, aisha 640");
        System.out.println();
        System.out.printf("      %-8s %-18s %s%n", "player", "competition rank", "dense rank");
        for (String player : List.of("priya", "rahul", "meera", "sam", "dev", "aisha")) {
            System.out.printf("      %-8s %-18d %d%n", player, rank(ranked.rankOf(player)), rank(ranked.denseRankOf(player)));
        }
        System.out.println();
        System.out.println("  Competition (1,2,2,4) is what a scoreboard shows - the tied pair consumes");
        System.out.println("  both slots. Dense (1,2,2,3) is what game UIs usually want - no gaps.");
        System.out.println("  Ask which one they mean. Picking silently is a 50/50 bet you do not need to take.");

        section("6. 'Show me my neighbours' - the follow-up that justifies the TreeSet");
        print(ranked.around("sam", 2));
        System.out.println("  A rank index alone cannot answer this: knowing you are number 4 does not tell");
        System.out.println("  you WHO number 3 is. headSet().descendingIterator() walks up and tailSet()");
        System.out.println("  walks down, both O(log n + radius) - no scan, no sort.");

        section("7. Scoring rules change what the board even means");
        long[] submissions = {300, 500, 150};
        for (ScoringRule rule : List.of(ScoringRule.ACCUMULATE, ScoringRule.BEST, ScoringRule.LATEST)) {
            Leaderboard board = newBoard("rule-demo", rule, 0);
            long last = 0;
            for (long points : submissions) {
                last = board.submit("priya", points);
            }
            System.out.printf("      %-11s after submitting 300, 500, 150 -> %d%n", rule.name(), last);
        }
        System.out.println("  ACCUMULATE = career points. BEST = high score, so that last bad round of 150");
        System.out.println("  never hurts her. LATEST = current Elo or level, where the bad round is all");
        System.out.println("  that counts. Three different products from one interface - and 'which of these");
        System.out.println("  did you mean?' is a requirements question worth asking before you write code.");

        section("8. Observer: reacting to the top 3 without the board knowing how");
        Leaderboard watched = newBoard("watched", ScoringRule.BEST, 3);
        RecordingListener audit = new RecordingListener();
        watched.addListener(audit);
        watched.addListener(new BrokenListener());

        System.out.println("  three players submit, filling the top 3:");
        watched.submit("priya", 500);
        watched.submit("rahul", 400);
        watched.submit("meera", 300);
        audit.drain().forEach(line -> System.out.println("      " + line));

        System.out.println("  dev arrives on 450 and pushes meera out:");
        watched.submit("dev", 450);
        audit.drain().forEach(line -> System.out.println("      " + line));

        System.out.println("  A second listener threw on every one of those four submissions (the ignored");
        System.out.println("  lines above). The recording listener still got every event and not one score");
        System.out.println("  submission failed - each subscriber is wrapped in its own try/catch, outside");
        System.out.println("  the board's write lock.");

        section("9. Rank index: the O(n) answer vs the O(log range) answer");
        int players = 100_000;
        int queries = 200;
        benchmark(players, queries);

        section("10. Daily, weekly and all-time");
        LeaderboardService service = new LeaderboardService(
                ScoringRule.ACCUMULATE, () -> new FenwickRankIndex(100_000), 3);
        Instant monday = Instant.parse("2026-07-27T09:00:00Z");
        Instant tuesday = monday.plus(1, ChronoUnit.DAYS);

        service.submit("ranked", "priya", 300, monday);
        service.submit("ranked", "rahul", 500, monday);
        service.submit("ranked", "priya", 400, tuesday);

        System.out.println("  boards created by 3 submissions: " + service.boardCount());
        service.boardNames().forEach(name -> System.out.println("      " + name));
        System.out.println();
        System.out.println("  Tuesday daily  : " + service.topK("ranked", TimeWindow.DAILY, tuesday, 3));
        System.out.println("  Monday daily   : " + service.topK("ranked", TimeWindow.DAILY, monday, 3));
        System.out.println("  This week      : " + service.topK("ranked", TimeWindow.WEEKLY, tuesday, 3));
        System.out.println("  All time       : " + service.topK("ranked", TimeWindow.ALL_TIME, tuesday, 3));
        System.out.println();
        System.out.println("  priya leads all-time on 700 but lost Monday to rahul. One submission wrote to");
        System.out.println("  three pre-aggregated boards, so every read is already sorted - no filtering");
        System.out.println("  over submission history on the hottest query in the app.");

        int purged = service.purgeDailyBoardsBefore(tuesday.plus(30, ChronoUnit.DAYS), 7);
        System.out.println("  30 days later, purgeDailyBoardsBefore(retention=7) dropped " + purged + " boards.");
        System.out.println("  Expiry is a key removal precisely BECAUSE the data was bucketed. Mention the");
        System.out.println("  cleanup even if you run out of time to write it.");

        section("11. Concurrency: 8 threads, exact totals");
        Leaderboard contested = newBoard("contested", ScoringRule.ACCUMULATE, 0);
        int threads = 8;
        int submissionsPerThread = 250;
        List<String> roster = List.of("priya", "rahul", "meera", "sam");

        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    startGun.await();
                    for (int i = 0; i < submissionsPerThread; i++) {
                        for (String player : roster) {
                            contested.submit(player, 1);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        startGun.countDown();
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);

        long expected = (long) threads * submissionsPerThread;
        boolean allExact = true;
        for (String player : roster) {
            long actual = contested.scoreOf(player).orElse(-1);
            allExact &= actual == expected;
            System.out.printf("      %-7s %d (expected %d)%n", player, actual, expected);
        }
        System.out.println("  every total exact: " + allExact);
        System.out.println("  The read-modify-write in submit() spans three structures, so it has to be");
        System.out.println("  atomic across all of them. A ConcurrentHashMap would make the map thread-safe");
        System.out.println("  and still lose updates, because the lost update is in the read-then-write,");
        System.out.println("  not in the map. Reads use the read lock, so the top-10 query never blocks");
        System.out.println("  another top-10 query - which matters when reads outnumber writes 1000:1.");

        section("12. What to say about real scale");
        System.out.println("  - In production this class is a Redis sorted set. ZADD is O(log n), ZREVRANGE");
        System.out.println("    is O(log n + k) and ZREVRANK is O(log n) - the skip list gives you exactly");
        System.out.println("    what the TreeSet plus rank index gives here, in one primitive.");
        System.out.println("  - Fenwick memory scales with the score RANGE, not the player count. Bucket or");
        System.out.println("    coordinate-compress the scores if the range is huge or fractional.");
        System.out.println("  - Shard by game mode and window first; those are natural, already-isolated");
        System.out.println("    partitions. Sharding one board is much harder because rank is global.");
        System.out.println("  - Only the top ~1000 needs to be exact. Below that, an approximate rank from");
        System.out.println("    a periodically rebuilt histogram is indistinguishable to the player and");
        System.out.println("    removes the write-path contention entirely.");
        System.out.println("  - Anti-cheat and idempotency belong on the write path: a replayed match result");
        System.out.println("    must not score twice. That is an idempotency key on the submission id.");

        System.out.println("\nDone.");
    }

    private static void benchmark(int players, int queries) {
        Random random = new Random(42); // fixed seed - the comparison must be reproducible
        int[] scores = new int[players];
        for (int i = 0; i < players; i++) {
            scores[i] = random.nextInt(100_000);
        }

        Leaderboard scanBoard = new Leaderboard("scan", ScoringRule.BEST, new ScanRankIndex(), 0);
        Leaderboard fenwickBoard = new Leaderboard("fenwick", ScoringRule.BEST, new FenwickRankIndex(100_000), 0);
        for (int i = 0; i < players; i++) {
            scanBoard.submit("p" + i, scores[i]);
            fenwickBoard.submit("p" + i, scores[i]);
        }
        System.out.println("  " + players + " players loaded into both boards");

        List<String> sample = new ArrayList<>(queries);
        for (int i = 0; i < queries; i++) {
            sample.add("p" + random.nextInt(players));
        }

        long scanNanos = timeRankQueries(scanBoard, sample);
        long fenwickNanos = timeRankQueries(fenwickBoard, sample);

        boolean identical = true;
        for (String player : sample) {
            identical &= scanBoard.rankOf(player).orElse(-1) == fenwickBoard.rankOf(player).orElse(-2);
        }

        System.out.printf("      %-22s %d rank queries in %6.1f ms%n", scanBoard.rankIndexName(), queries,
                scanNanos / 1_000_000.0);
        System.out.printf("      %-22s %d rank queries in %6.1f ms%n", fenwickBoard.rankIndexName(), queries,
                fenwickNanos / 1_000_000.0);
        System.out.println("      answers identical: " + identical);
        System.out.println("      speed-up: about " + Math.max(1, scanNanos / Math.max(1, fenwickNanos)) + "x");
        System.out.println();
        System.out.println("  Same API, same answers, swapped by one constructor argument - that is the");
        System.out.println("  payoff for making rank a Strategy instead of a method body. Note the shape of");
        System.out.println("  the win: the scan cost grows with the player count, the Fenwick cost grows with");
        System.out.println("  the log of the score range. At a million players the gap is another 10x.");
    }

    private static long timeRankQueries(Leaderboard board, List<String> sample) {
        long start = System.nanoTime();
        long sink = 0;
        for (String player : sample) {
            sink += board.rankOf(player).orElse(0);
        }
        long elapsed = System.nanoTime() - start;
        if (sink < 0) {
            throw new IllegalStateException("unreachable; keeps the loop from being optimised away");
        }
        return elapsed;
    }

    private static Leaderboard newBoard(String name, ScoringRule rule, int trackedTop) {
        return new Leaderboard(name, rule, new FenwickRankIndex(100_000), trackedTop);
    }

    private static long rank(OptionalLong value) {
        return value.orElse(-1);
    }

    private static void print(List<RankedPlayer> page) {
        page.forEach(player -> System.out.println("      " + player));
    }

    private static List<String> names(Iterable<Entry> entries) {
        List<String> out = new ArrayList<>();
        entries.forEach(entry -> out.add(entry.playerId()));
        return out;
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    /**
     * Deliberately mutable, and deliberately only used to show what mutability costs you.
     *
     * <p>No {@code equals} or {@code hashCode} on purpose: a {@code TreeSet} ignores both and decides
     * everything from the comparator, which is itself a detail worth knowing — a class can be
     * perfectly well-behaved under {@code equals} and still be lost by a sorted set.
     */
    static final class MutableEntry {
        final String playerId;
        long score;

        MutableEntry(String playerId, long score) {
            this.playerId = playerId;
            this.score = score;
        }

        @Override
        public String toString() {
            return playerId + ":" + score;
        }
    }

    /** Captures events so the demo can print them; a real one would push notifications. */
    static final class RecordingListener implements LeaderboardListener {
        private final List<String> events = new ArrayList<>();

        @Override
        public void onEnteredTop(String board, String playerId, long rank) {
            events.add(playerId + " entered the top 3 at #" + rank);
        }

        @Override
        public void onDisplacedFromTop(String board, String playerId) {
            events.add(playerId + " dropped out of the top 3");
        }

        List<String> drain() {
            List<String> snapshot = new ArrayList<>(events);
            events.clear();
            return snapshot;
        }
    }

    /** Proves one bad subscriber cannot break the others or the submission itself. */
    static final class BrokenListener implements LeaderboardListener {
        @Override
        public void onScoreUpdated(String board, String playerId, long oldScore, long newScore) {
            throw new IllegalStateException("analytics sink unreachable");
        }
    }
}
