package problems.cache;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * LEAST FREQUENTLY USED — evict whoever has been read the fewest times.
 *
 * <p><b>Why this exists as a separate class.</b> "Now make it LFU" is the standard follow-up to the
 * LRU question, and it is a genuinely different structure — not a tweak. If your LRU cache had the
 * doubly linked list welded into it, you are starting over. Behind {@link EvictionPolicy} you are
 * not.
 *
 * <p><b>Getting O(1), which is the hard part.</b> The obvious design is a {@code Map<K, Integer>} of
 * counts plus a scan for the minimum at eviction time — O(n) per eviction, and the interviewer will
 * catch it. The O(1) construction is three pieces working together:
 *
 * <ul>
 *   <li>{@code frequency}: key &rarr; how many times it has been read.</li>
 *   <li>{@code buckets}: count &rarr; the set of keys with exactly that count.</li>
 *   <li>{@code minFrequency}: the smallest non-empty bucket, maintained incrementally.</li>
 * </ul>
 *
 * <p>A read moves a key from bucket <i>f</i> to bucket <i>f+1</i> — two hash operations. Eviction
 * reads from {@code buckets[minFrequency]} directly, never searching. The invariant that makes
 * {@code minFrequency} cheap: after a read it can only stay the same or increase by exactly one,
 * and it increases only when the bucket it pointed at just became empty.
 *
 * <p><b>Why {@link LinkedHashSet} for the buckets.</b> Ties are common — on a cold cache everything
 * has a count of 1 — so the policy needs a tie-break. Insertion-ordered iteration means the oldest
 * key in the bucket goes first, making this <em>LFU with LRU tie-breaking</em>, which is what
 * production caches actually do. A plain {@code HashSet} would evict an arbitrary key and make your
 * cache's behaviour untestable. This detail is worth saying out loud; most candidates miss it.
 *
 * <p><b>The known weakness, worth volunteering:</b> LFU suffers from <em>cache pollution</em>. A key
 * hammered a million times during a batch job keeps a high count forever and never leaves, even
 * once it is irrelevant. Real systems fix this by ageing counts, or by using a windowed variant such
 * as W-TinyLFU (what Caffeine ships). Naming that trade-off unprompted lands well.
 */
public class LfuEvictionPolicy<K> implements EvictionPolicy<K> {

    private final Map<K, Integer> frequency = new HashMap<>();

    /** TreeMap only so the demo can print buckets in a stable order; a HashMap is enough for O(1). */
    private final Map<Integer, LinkedHashSet<K>> buckets = new TreeMap<>();

    private int minFrequency = 0;

    @Override
    public void keyInserted(K key) {
        if (frequency.containsKey(key)) {
            keyAccessed(key);
            return;
        }
        frequency.put(key, 1);
        buckets.computeIfAbsent(1, f -> new LinkedHashSet<>()).add(key);
        minFrequency = 1; // a brand new key always resets the floor
    }

    @Override
    public void keyAccessed(K key) {
        Integer current = frequency.get(key);
        if (current == null) {
            keyInserted(key);
            return;
        }

        LinkedHashSet<K> bucket = buckets.get(current);
        bucket.remove(key);
        if (bucket.isEmpty()) {
            buckets.remove(current);
            // The only case where the floor can move: we just emptied the bucket it pointed at.
            if (minFrequency == current) {
                minFrequency = current + 1;
            }
        }

        int promoted = current + 1;
        frequency.put(key, promoted);
        buckets.computeIfAbsent(promoted, f -> new LinkedHashSet<>()).add(key);
    }

    @Override
    public void keyRemoved(K key) {
        Integer current = frequency.remove(key);
        if (current == null) {
            return;
        }
        LinkedHashSet<K> bucket = buckets.get(current);
        bucket.remove(key);
        if (bucket.isEmpty()) {
            buckets.remove(current);
            if (minFrequency == current) {
                // Only reachable via an explicit remove(), not via eviction, so the cost of
                // recomputing the floor here does not affect the hot path.
                minFrequency = buckets.keySet().stream().mapToInt(Integer::intValue).min().orElse(0);
            }
        }
    }

    @Override
    public Optional<K> evictionCandidate() {
        LinkedHashSet<K> bucket = buckets.get(minFrequency);
        if (bucket == null || bucket.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bucket.iterator().next()); // oldest key at the lowest count
    }

    @Override
    public void clear() {
        frequency.clear();
        buckets.clear();
        minFrequency = 0;
    }

    @Override
    public String name() {
        return "LFU";
    }

    @Override
    public String toString() {
        return buckets.toString();
    }
}
