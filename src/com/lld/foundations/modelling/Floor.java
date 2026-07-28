package com.lld.foundations.modelling;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ownership, and what it actually buys you.
 *
 * <p>A {@link Spot} cannot exist without a floor, so this is <b>composition</b>: the floor
 * <em>creates</em> its spots (note the package-private {@code Spot} constructor and mutators - nobody
 * outside this package can make or occupy one), it enforces the invariants, and it is the only thing
 * allowed to mutate them.
 *
 * <p>Ownership decides three things at once: <b>who constructs it, who validates it, and who may
 * mutate it</b>. All three collapse the moment you hand out the internal list, which is why
 * {@link #spotsUnsafe()} exists here only as a thing the demo can break.
 *
 * <p><b>The best accessor is the one you do not write.</b> {@link #findFreeSpot()} is what every
 * caller actually wanted; returning the collection at all was an answer to a question nobody asked.
 */
public final class Floor {

    private final int level;
    private final List<Spot> spots = new ArrayList<>();

    public Floor(int level, int spotCount) {
        this.level = level;
        for (int i = 1; i <= spotCount; i++) {
            spots.add(new Spot(i));
        }
    }

    /** Leaks the live list. Any caller can now clear it, and the floor cannot stop them. */
    public List<Spot> spotsUnsafe() {
        return spots;
    }

    /** Safe: a caller can look, and any attempt to mutate fails loudly at the call site. */
    public List<Spot> spots() {
        return List.copyOf(spots);
    }

    /** Better still: no collection escapes, and the floor keeps its invariant. */
    public Optional<Spot> findFreeSpot() {
        return spots.stream().filter(Spot::isFree).findFirst();
    }

    public Optional<Spot> claimFreeSpot() {
        Optional<Spot> free = findFreeSpot();
        free.ifPresent(Spot::occupy);
        return free;
    }

    public int size() {
        return spots.size();
    }

    public int level() {
        return level;
    }
}
