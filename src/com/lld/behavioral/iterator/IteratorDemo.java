package com.lld.behavioral.iterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * ITERATOR — provide sequential access to elements of a collection without exposing its internal
 * representation. Also lets one collection support multiple traversal orders.
 *
 * When to use in LLD:
 *   - Custom collections (playlist, paginated API results, BST traversal, feed), or when you want
 *     to iterate the same data in different orders without leaking the backing structure.
 *
 * In Java you usually implement java.util.Iterable/Iterator so the object works with for-each.
 */

class Song {
    final String title;
    Song(String title) { this.title = title; }
    @Override public String toString() { return title; }
}

/** Custom collection exposing two different traversal strategies. */
class Playlist implements Iterable<Song> {
    private final List<Song> songs = new ArrayList<>();

    Playlist add(Song s) { songs.add(s); return this; }

    /** Default traversal: in order. Enables for-each. */
    @Override public Iterator<Song> iterator() {
        return new Iterator<>() {
            private int index = 0;
            public boolean hasNext() { return index < songs.size(); }
            public Song next() {
                if (!hasNext()) throw new NoSuchElementException();
                return songs.get(index++);
            }
        };
    }

    /** A second traversal order over the SAME data — clients never see the backing List. */
    Iterator<Song> reverseIterator() {
        return new Iterator<>() {
            private int index = songs.size() - 1;
            public boolean hasNext() { return index >= 0; }
            public Song next() {
                if (!hasNext()) throw new NoSuchElementException();
                return songs.get(index--);
            }
        };
    }
}

public class IteratorDemo {
    public static void main(String[] args) {
        Playlist playlist = new Playlist()
                .add(new Song("One"))
                .add(new Song("Two"))
                .add(new Song("Three"));

        System.out.println("Forward:");
        for (Song s : playlist) System.out.println("  " + s);   // uses iterator()

        System.out.println("Reverse:");
        Iterator<Song> it = playlist.reverseIterator();
        while (it.hasNext()) System.out.println("  " + it.next());
    }
}
