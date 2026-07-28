package com.lld.problems.cache;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * LEAST RECENTLY USED — evict whoever has gone longest without being touched.
 *
 * <p><b>This is the data structure the whole question is really about.</b> The requirement is O(1)
 * for both {@code get} and {@code put}, and no single structure gives you that:
 *
 * <ul>
 *   <li>A {@code HashMap} alone has O(1) lookup but no notion of order.</li>
 *   <li>An {@code ArrayList} or {@code ArrayDeque} ordered by recency has O(n) "move this element to
 *       the front", because you must shift everything after it.</li>
 *   <li>A {@code TreeMap} keyed by access time is O(log n), which is close but is not what was
 *       asked for.</li>
 * </ul>
 *
 * <p>The answer is <b>both at once</b>: a hash map from key &rarr; node, plus a doubly linked list
 * holding the recency order. The map finds the node in O(1); because the node knows its own
 * neighbours, unlinking and relinking it is O(1) too. That combination is the insight being tested,
 * and it is worth drawing on the whiteboard before you write a line of code.
 *
 * <p><b>Why <em>doubly</em> linked.</b> With a singly linked list, unlinking a node requires its
 * predecessor, and finding that predecessor is an O(n) walk. The back-pointer is what buys the O(1).
 *
 * <p><b>Why sentinel head/tail nodes.</b> They are never removed, so every insert and unlink has a
 * non-null node on both sides. That deletes every "is this the first element?" and "is the list
 * empty?" branch — the empty-list and single-element cases, which is where hand-written linked list
 * code normally breaks.
 *
 * <p><b>In the wild:</b> {@code LinkedHashMap(capacity, loadFactor, accessOrder=true)} with
 * {@code removeEldestEntry} overridden is a production-grade LRU in about five lines, and it works
 * precisely because it is a hash map plus a linked list internally. Mention it — it shows you know
 * the JDK — then implement the structure by hand, because being shown the shortcut is not what the
 * interviewer is measuring.
 */
public class LruEvictionPolicy<K> implements EvictionPolicy<K> {

    /** Package-private, mutable, no getters: this is an internal node, not a domain object. */
    private static final class Node<K> {
        private final K key;
        private Node<K> prev;
        private Node<K> next;

        private Node(K key) {
            this.key = key;
        }
    }

    private final Map<K, Node<K>> nodes = new HashMap<>();

    /** head.next is the most recently used; tail.prev is the eviction victim. */
    private final Node<K> head = new Node<>(null);
    private final Node<K> tail = new Node<>(null);

    public LruEvictionPolicy() {
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public void keyAccessed(K key) {
        Node<K> node = nodes.get(key);
        if (node == null) {
            keyInserted(key);
            return;
        }
        unlink(node);
        linkAfterHead(node);
    }

    @Override
    public void keyInserted(K key) {
        Node<K> existing = nodes.get(key);
        if (existing != null) {
            keyAccessed(key);
            return;
        }
        Node<K> node = new Node<>(key);
        nodes.put(key, node);
        linkAfterHead(node);
    }

    @Override
    public void keyRemoved(K key) {
        Node<K> node = nodes.remove(key);
        if (node != null) {
            unlink(node);
        }
    }

    @Override
    public Optional<K> evictionCandidate() {
        Node<K> lru = tail.prev;
        return lru == head ? Optional.empty() : Optional.of(lru.key);
    }

    @Override
    public void clear() {
        nodes.clear();
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public String name() {
        return "LRU";
    }

    // ---------------------------------------------------------------- list surgery, both O(1)

    private void linkAfterHead(Node<K> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void unlink(Node<K> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
        node.prev = null;
        node.next = null;
    }

    /** Most-recent-first, for the demo output and for debugging. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (Node<K> n = head.next; n != tail; n = n.next) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append(n.key);
        }
        return sb.append(']').toString();
    }
}
