package problems.cache;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * FIRST IN, FIRST OUT — evict in insertion order, ignoring reads entirely.
 *
 * <p>Included for one reason: {@link #keyAccessed} is deliberately empty, and that empty method is
 * the clearest possible statement of what separates the three policies. LRU reorders on read, LFU
 * counts reads, FIFO does not care about reads at all.
 *
 * <p>Not a toy, either — FIFO is the right choice when entries expire for reasons unrelated to
 * usage, such as a cache of signed tokens where age is what matters and popularity is irrelevant.
 * It is also the cheapest of the three, with no per-read bookkeeping on the hot path.
 */
public class FifoEvictionPolicy<K> implements EvictionPolicy<K> {

    /** Insertion-ordered, and a Set so a duplicate insert cannot corrupt the queue. */
    private final Set<K> insertionOrder = new LinkedHashSet<>();

    @Override
    public void keyAccessed(K key) {
        // Intentionally empty. Reads do not buy you any time under FIFO.
    }

    @Override
    public void keyInserted(K key) {
        insertionOrder.add(key);
    }

    @Override
    public void keyRemoved(K key) {
        insertionOrder.remove(key);
    }

    @Override
    public Optional<K> evictionCandidate() {
        return insertionOrder.stream().findFirst();
    }

    @Override
    public void clear() {
        insertionOrder.clear();
    }

    @Override
    public String name() {
        return "FIFO";
    }

    @Override
    public String toString() {
        return insertionOrder.toString();
    }
}
