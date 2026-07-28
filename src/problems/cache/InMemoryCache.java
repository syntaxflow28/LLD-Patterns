package problems.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A bounded, optionally expiring, thread-safe cache with pluggable eviction.
 *
 * <p><b>The concurrency answer interviewers are looking for.</b> The instinct is
 * "use a {@code ConcurrentHashMap} and we're thread-safe". We are not. A {@code put} that overflows
 * capacity has to do three things — pick a victim, remove the victim, insert the newcomer — and the
 * eviction policy's linked list or frequency buckets must be updated in lock-step with the map. A
 * concurrent map makes each individual operation atomic but gives you no way to make that
 * <em>sequence</em> atomic. Two overflowing puts interleaving will corrupt the policy's internal
 * structure or blow past the capacity bound.
 *
 * <p>So the lock is around the compound operation, and the map underneath can be a plain
 * {@code HashMap}. Say exactly that: <em>"the map isn't the shared mutable state that matters — the
 * map plus the policy, together, is."</em>
 *
 * <p><b>How you would scale it past a single lock:</b> shard the cache into N independent segments
 * by {@code key.hashCode()}, each with its own lock and its own policy instance. That is what
 * {@code ConcurrentHashMap} does internally and what Guava's cache does explicitly. Eviction
 * becomes per-segment rather than global, which is a small accuracy loss for a large throughput
 * win — a good trade-off to offer before you are asked for one.
 *
 * <p><b>On expiry:</b> entries are evicted lazily, on read. No background reaper thread. That means
 * an expired entry that is never read again still occupies memory until it is evicted for capacity
 * reasons — the trade-off is no extra thread and no scanning. Production caches do the same, plus
 * opportunistic cleanup during writes.
 */
public class InMemoryCache<K, V> implements Cache<K, V> {

    /** A record cannot use the enclosing class's type parameter, so it declares its own. */
    private record Entry<T>(T value, Instant expiresAt) {

        boolean isExpiredAt(Instant now) {
            return expiresAt != null && !now.isBefore(expiresAt);
        }
    }

    private final int capacity;
    private final EvictionPolicy<K> policy;
    private final Duration timeToLive;
    private final Clock clock;

    private final Map<K, Entry<V>> store = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    private long hits;
    private long misses;
    private long evictions;
    private long expirations;

    public InMemoryCache(int capacity, EvictionPolicy<K> policy) {
        this(capacity, policy, null, Clock.systemUTC());
    }

    public InMemoryCache(int capacity, EvictionPolicy<K> policy, Duration timeToLive, Clock clock) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.capacity = capacity;
        this.policy = Objects.requireNonNull(policy, "policy");
        this.timeToLive = timeToLive;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<V> get(K key) {
        lock.lock();
        try {
            Entry<V> entry = store.get(key);
            if (entry == null) {
                misses++;
                return Optional.empty();
            }
            if (entry.isExpiredAt(clock.instant())) {
                drop(key);
                expirations++;
                misses++;
                return Optional.empty();
            }
            policy.keyAccessed(key);
            hits++;
            return Optional.of(entry.value());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        Objects.requireNonNull(key, "key");
        lock.lock();
        try {
            if (store.containsKey(key)) {
                // An overwrite is an access, not an insertion: it must not create a second
                // entry in the policy, and under LFU it should bump the frequency.
                store.put(key, newEntry(value));
                policy.keyAccessed(key);
                return;
            }
            if (store.size() >= capacity) {
                evictOne();
            }
            store.put(key, newEntry(value));
            policy.keyInserted(key);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean remove(K key) {
        lock.lock();
        try {
            if (!store.containsKey(key)) {
                return false;
            }
            drop(key);
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return store.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Current keys, without disturbing recency or frequency.
     *
     * <p>Exists so tests and the demo can assert what was evicted. Using {@code get()} to inspect a
     * cache is self-defeating — the observation changes the thing being observed.
     */
    public Set<K> keys() {
        lock.lock();
        try {
            return new LinkedHashSet<>(store.keySet());
        } finally {
            lock.unlock();
        }
    }

    public CacheStats stats() {
        lock.lock();
        try {
            return new CacheStats(hits, misses, evictions, expirations, store.size(), capacity);
        } finally {
            lock.unlock();
        }
    }

    public String policyName() {
        return policy.name();
    }

    // ---------------------------------------------------------------- internals (lock always held)

    private Entry<V> newEntry(V value) {
        Instant expiresAt = timeToLive == null ? null : clock.instant().plus(timeToLive);
        return new Entry<>(value, expiresAt);
    }

    private void evictOne() {
        K victim = policy.evictionCandidate()
                .orElseThrow(() -> new IllegalStateException(
                        "Cache is full but the policy has no candidate - policy and store are out of sync"));
        drop(victim);
        evictions++;
    }

    /** The one place a key leaves. Keeping it single means store and policy cannot drift apart. */
    private void drop(K key) {
        store.remove(key);
        policy.keyRemoved(key);
    }

    public record CacheStats(long hits, long misses, long evictions, long expirations,
                             int size, int capacity) {

        public double hitRate() {
            long total = hits + misses;
            return total == 0 ? 0.0 : (double) hits / total;
        }

        @Override
        public String toString() {
            return String.format("size=%d/%d hits=%d misses=%d evictions=%d expired=%d hitRate=%.0f%%",
                    size, capacity, hits, misses, evictions, expirations, hitRate() * 100);
        }
    }
}
