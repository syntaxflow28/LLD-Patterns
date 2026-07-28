package com.lld.patterns.structural.flyweight;

import java.util.HashMap;
import java.util.Map;

/*
 * FLYWEIGHT — share common (intrinsic) state between many objects to cut memory usage. State that
 * differs per object (extrinsic) is passed in at call time instead of being stored.
 *
 * When to use in LLD:
 *   - Millions of similar objects: characters in a text editor, tiles/trees in a game map,
 *     particles, chess pieces, map markers.
 *
 * Intrinsic  = shared + immutable (glyph shape, tree texture, piece colour/type).
 * Extrinsic  = unique per instance (x/y position, board square) -> passed as method args.
 */

/** Flyweight: holds ONLY intrinsic, immutable, shareable state. */
class TreeType {
    private final String name;
    private final String colour;
    private final String texture;   // imagine a heavy texture blob

    TreeType(String name, String colour, String texture) {
        this.name = name; this.colour = colour; this.texture = texture;
        System.out.println("  (created heavy TreeType: " + name + ")");
    }

    /** Extrinsic state (x, y) is supplied by the caller, not stored. */
    void draw(int x, int y) {
        System.out.println("Draw " + name + "/" + colour + " [" + texture + "] at (" + x + "," + y + ")");
    }
}

/** Factory that guarantees sharing — returns an existing flyweight when one matches. */
class TreeTypeFactory {
    private static final Map<String, TreeType> CACHE = new HashMap<>();

    static TreeType get(String name, String colour, String texture) {
        String key = name + "|" + colour + "|" + texture;
        return CACHE.computeIfAbsent(key, k -> new TreeType(name, colour, texture));
    }

    static int distinctTypes() { return CACHE.size(); }
}

/** Context: lightweight object storing only the extrinsic state + a flyweight reference. */
class Tree {
    private final int x, y;
    private final TreeType type;
    Tree(int x, int y, TreeType type) { this.x = x; this.y = y; this.type = type; }
    void draw() { type.draw(x, y); }
}

public class FlyweightDemo {
    public static void main(String[] args) {
        Tree[] forest = new Tree[5];
        // 5 trees, but only 2 heavy TreeType objects get allocated.
        forest[0] = new Tree(1, 1, TreeTypeFactory.get("Oak", "green", "oak.png"));
        forest[1] = new Tree(4, 7, TreeTypeFactory.get("Oak", "green", "oak.png"));
        forest[2] = new Tree(9, 2, TreeTypeFactory.get("Pine", "dark-green", "pine.png"));
        forest[3] = new Tree(3, 3, TreeTypeFactory.get("Oak", "green", "oak.png"));
        forest[4] = new Tree(8, 8, TreeTypeFactory.get("Pine", "dark-green", "pine.png"));

        for (Tree t : forest) t.draw();
        System.out.println("Trees drawn: " + forest.length + ", distinct TreeType objects: "
                + TreeTypeFactory.distinctTypes());
    }
}
