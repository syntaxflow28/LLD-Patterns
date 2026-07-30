package com.lld.problems.amazonlocker;

/**
 * Locker sizes, and the sizes parcels are graded into.
 *
 * <p><b>One enum for both, deliberately.</b> A parcel is not measured in centimetres here — it is
 * graded into the smallest bucket it fits in, exactly as a warehouse does when it picks a box. That
 * turns "does this parcel fit in this locker?" into an integer comparison instead of a rotation
 * search. Say this out loud: <em>"I'm modelling size as a grade, not as dimensions, because fitting
 * arbitrary boxes into arbitrary holes is 3-D bin packing and it is not what this question is
 * about."</em>
 *
 * <p><b>When the shortcut breaks.</b> If the interviewer says "a yoga mat is long but thin", the
 * grade collapses and you need real {@code Dimensions(l, w, h)} with a {@code fitsIn} that tries the
 * six axis-aligned orientations. Mention it as the extension; do not build it up front.
 *
 * <p>Ordinal is <em>not</em> used for the comparison — an explicit {@code capacity} field is, so
 * that inserting a new size in the middle of the enum cannot silently reorder the fit logic.
 */
public enum LockerSize {

    SMALL(1),
    MEDIUM(2),
    LARGE(3),
    EXTRA_LARGE(4);

    private final int capacity;

    LockerSize(int capacity) {
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    /** True if a locker of this size can hold a parcel graded {@code parcelSize}. */
    public boolean accommodates(LockerSize parcelSize) {
        return this.capacity >= parcelSize.capacity;
    }
}
