package problems.cache;

import java.util.Optional;

/**
 * STRATEGY — when the cache is full, who gets thrown out?
 *
 * <p>This is the axis the interviewer will change on you. You will design an LRU cache, and then
 * they will say "now make it LFU" — and if eviction is welded into the cache class you are
 * rewriting the whole thing. Behind this interface it is one new class and zero edits to
 * {@link InMemoryCache}.
 *
 * <p><b>The contract that makes O(1) possible.</b> The cache tells the policy about every event
 * ({@code inserted}, {@code accessed}, {@code removed}) as it happens, so the policy maintains its
 * own ordering incrementally. The naive alternative — storing a {@code lastAccessedAt} timestamp on
 * each entry and scanning for the minimum at eviction time — is O(n) per eviction and is the answer
 * that gets rejected. If your policy needs to search at eviction time, your data structure is
 * wrong.
 *
 * @param <K> the key type; policies track keys only, never values, so they stay memory-cheap
 */
public interface EvictionPolicy<K> {

    /** A key was read (a cache hit). */
    void keyAccessed(K key);

    /** A brand new key entered the cache. */
    void keyInserted(K key);

    /** A key left the cache, either by eviction or by explicit removal. */
    void keyRemoved(K key);

    /** @return the key that should be evicted next, or empty if the policy is tracking nothing */
    Optional<K> evictionCandidate();

    void clear();

    String name();
}
