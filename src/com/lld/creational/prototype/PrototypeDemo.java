package com.lld.creational.prototype;

import java.util.ArrayList;
import java.util.List;

/**
 * PROTOTYPE — create new objects by cloning an existing "prototype" instead of building from
 * scratch. Useful when construction is expensive or when you want to spawn many similar objects
 * from a configured template.
 *
 * When to use in LLD:
 *   - Game entities (spawn 100 enemies from one template), document templates, pre-configured
 *     objects registered in a registry and cloned on demand.
 *
 * Note the deep vs shallow copy decision — clone mutable nested state to avoid shared references.
 */

class Enemy implements Cloneable {
    private String type;
    private int health;
    private List<String> abilities;      // mutable nested state -> needs deep copy

    Enemy(String type, int health, List<String> abilities) {
        this.type = type;
        this.health = health;
        this.abilities = abilities;
    }

    void setHealth(int health) { this.health = health; }

    /** Deep clone: copy the list so clones don't share the same ability list. */
    @Override public Enemy clone() {
        return new Enemy(this.type, this.health, new ArrayList<>(this.abilities));
    }

    @Override public String toString() {
        return type + "{hp=" + health + ", abilities=" + abilities + "}";
    }
}

public class PrototypeDemo {
    public static void main(String[] args) {
        Enemy orcTemplate = new Enemy("Orc", 100, new ArrayList<>(List.of("smash", "block")));

        // Spawn variations by cloning + tweaking, instead of re-specifying everything.
        Enemy woundedOrc = orcTemplate.clone();
        woundedOrc.setHealth(40);

        Enemy healthyOrc = orcTemplate.clone();

        System.out.println("Template : " + orcTemplate);
        System.out.println("Clone 1  : " + woundedOrc);
        System.out.println("Clone 2  : " + healthyOrc);
    }
}
