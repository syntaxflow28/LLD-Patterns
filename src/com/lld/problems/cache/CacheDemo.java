package com.lld.problems.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable walk-through of the cache design.
 *
 * <pre>
 *   java -cp out com.lld.problems.cache.CacheDemo
 * </pre>
 *
 * <p>This is the single most frequently asked LLD question. Two things are being measured:
 * <ol>
 *   <li><b>Data structures</b> — can you get O(1) get <em>and</em> put? (hash map + doubly linked
 *       list for LRU; frequency buckets + a maintained minimum for LFU)</li>
 *   <li><b>Design</b> — when they say "now make it LFU", how much of your code changes? (here:
 *       none of it)</li>
 * </ol>
 *
 * <p>Lead with the data structure. Candidates who open with patterns and leave the O(1) argument
 * until the end usually run out of time before proving the part that actually counts.
 */
public class CacheDemo {

    public static void main(String[] args) throws Exception {

        section("1. The same access trace, three policies, three different victims");
        System.out.println("  trace: put A,B,C  ->  get A x3  ->  get B  ->  get C  ->  put D (capacity 3)");
        System.out.println();
        runTrace(new LruEvictionPolicy<>(),
                "evicts A: it was read three times, but longest ago. LRU is blind to how HOT a key is.");
        runTrace(new LfuEvictionPolicy<>(),
                "evicts B: A has 4 reads, B and C have 2 each, and B is the older of the tied pair.");
        runTrace(new FifoEvictionPolicy<>(),
                "evicts A: first in, first out. Reads never mattered.");
        System.out.println("  Same trace, same capacity, different answers - that is why eviction is a Strategy.");

        section("2. LRU recency order is maintained on every read");
        LruEvictionPolicy<String> lru = new LruEvictionPolicy<>();
        InMemoryCache<String, String> cache = new InMemoryCache<>(4, lru);
        for (String key : List.of("A", "B", "C", "D")) {
            cache.put(key, key.toLowerCase());
        }
        System.out.println("  after inserts        MRU->LRU " + lru);
        cache.get("A");
        System.out.println("  after get(A)         MRU->LRU " + lru);
        cache.get("C");
        System.out.println("  after get(C)         MRU->LRU " + lru);
        cache.put("E", "e");
        System.out.println("  after put(E)         MRU->LRU " + lru + "   (B was the tail, so B went)");

        section("3. LFU frequency buckets");
        LfuEvictionPolicy<String> lfu = new LfuEvictionPolicy<>();
        InMemoryCache<String, String> counted = new InMemoryCache<>(3, lfu);
        counted.put("X", "1");
        counted.put("Y", "2");
        counted.put("Z", "3");
        counted.get("X");
        counted.get("X");
        counted.get("Y");
        System.out.println("  buckets freq->keys   " + lfu);
        System.out.println("  eviction candidate   " + lfu.evictionCandidate().orElseThrow()
                + "   (lowest bucket, oldest key in it)");

        section("4. Time-to-live, verified without sleeping");
        MutableClock clock = new MutableClock(Instant.parse("2026-07-27T12:00:00Z"));
        InMemoryCache<String, String> session = new InMemoryCache<>(
                10, new LruEvictionPolicy<>(), Duration.ofMinutes(30), clock);
        session.put("token", "abc123");
        System.out.println("  t+0m   get(token) -> " + session.get("token"));
        clock.advance(Duration.ofMinutes(29));
        System.out.println("  t+29m  get(token) -> " + session.get("token"));
        clock.advance(Duration.ofMinutes(2));
        System.out.println("  t+31m  get(token) -> " + session.get("token") + "  (expired lazily, on read)");
        System.out.println("  " + session.stats());
        System.out.println("  Injecting a Clock is why this test runs in microseconds instead of 31 minutes.");

        section("5. Overwriting an existing key must not double-count");
        InMemoryCache<String, String> overwrite = new InMemoryCache<>(2, new LruEvictionPolicy<>());
        overwrite.put("K", "v1");
        overwrite.put("K", "v2");
        overwrite.put("K", "v3");
        System.out.println("  put(K,..) three times -> size " + overwrite.size() + " (expected 1)");
        System.out.println("  value is " + overwrite.get("K").orElseThrow() + ", capacity untouched");

        section("6. Stats");
        InMemoryCache<String, String> measured = new InMemoryCache<>(2, new LruEvictionPolicy<>());
        measured.put("A", "1");
        measured.put("B", "2");
        measured.get("A");
        measured.get("A");
        measured.get("missing");
        measured.put("C", "3");
        System.out.println("  " + measured.stats());

        section("7. Concurrency: 8 threads hammering a capacity-100 cache");
        stressTest();

        System.out.println("\nDone.");
    }

    /** Runs one identical trace through whichever policy is supplied and reports the survivor set. */
    private static void runTrace(EvictionPolicy<String> policy, String explanation) {
        InMemoryCache<String, String> cache = new InMemoryCache<>(3, policy);
        cache.put("A", "1");
        cache.put("B", "2");
        cache.put("C", "3");
        cache.get("A");
        cache.get("A");
        cache.get("A");
        cache.get("B");
        cache.get("C");
        cache.put("D", "4");

        Set<String> survivors = cache.keys();
        String evicted = Set.of("A", "B", "C").stream()
                .filter(key -> !survivors.contains(key))
                .findFirst()
                .orElse("none");
        System.out.printf("  %-5s evicted %s  ->  %s%n", policy.name(), evicted, explanation);
    }

    /**
     * The capacity bound is the invariant worth proving under load: if the lock did not cover the
     * "evict then insert" sequence, concurrent overflowing puts would let the cache grow past its
     * limit — a slow memory leak that only shows up in production.
     */
    private static void stressTest() throws InterruptedException {
        int capacity = 100;
        InMemoryCache<Integer, String> cache = new InMemoryCache<>(capacity, new LruEvictionPolicy<>());
        AtomicInteger overCapacity = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int thread = 0; thread < 8; thread++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 5_000; i++) {
                        int key = (int) (Math.random() * 500);
                        cache.put(key, "v" + key);
                        cache.get(key);
                        if (cache.size() > capacity) {
                            overCapacity.incrementAndGet();
                        }
                    }
                } catch (RuntimeException ex) {
                    errors.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("  40,000 operations across 8 threads");
        System.out.println("  capacity breaches : " + overCapacity.get() + " (expected 0)");
        System.out.println("  exceptions        : " + errors.get() + " (expected 0)");
        System.out.println("  final size        : " + cache.size() + " (expected " + capacity + ")");
        System.out.println("  " + cache.stats());
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    /** Test double for time, so TTL behaviour is deterministic. */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
