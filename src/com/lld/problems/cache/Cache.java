package com.lld.problems.cache;

import java.util.Optional;

/**
 * The contract a cache exposes. Deliberately tiny.
 *
 * <p><b>Why {@link Optional} instead of returning null.</b> A cache has three outcomes, not two:
 * "here is the value", "I don't have it", and "I have it and it is genuinely null". Returning null
 * collapses the last two, which is exactly the bug that makes people wrap caches in
 * {@code containsKey} checks — and {@code containsKey} followed by {@code get} is a race in any
 * concurrent cache. Optional removes the ambiguity and the race at the same time.
 *
 * <p>Note what is <em>not</em> here: no {@code evict()}, no {@code capacity()}, no
 * {@code setPolicy()}. Eviction is the cache's business, not the caller's. Interface Segregation:
 * the reader of a cache should not be able to reconfigure it.
 */
public interface Cache<K, V> {

    Optional<V> get(K key);

    void put(K key, V value);

    boolean remove(K key);

    int size();
}
