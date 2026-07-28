package com.lld.foundations.modelling;

/**
 * The subclass that passes the "is-a" test in English and fails it in code.
 *
 * <p>To stay a square, it must break the promise {@link Rectangle} makes: setting the width also sets
 * the height. Any caller written correctly against {@code Rectangle} - "set width 5, set height 4,
 * expect area 20" - now gets 16, and there is nothing wrong with that caller.
 *
 * <p><b>The test that decides inheritance in one second:</b> can I hand this to <em>every</em> piece
 * of code that expects the parent and have it behave correctly, with no caller ever checking the
 * type? One {@code instanceof} needed anywhere means the answer is no.
 *
 * <p><b>The fix is composition.</b> A square <em>has-a</em> side length. Give both a common
 * {@code Shape} interface that exposes {@code area()} and no mutable width, and the problem
 * disappears - because the impossible promise was never made.
 */
public class Square extends Rectangle {

    @Override
    public void setWidth(int width) {
        super.setWidth(width);
        super.setHeight(width);
    }

    @Override
    public void setHeight(int height) {
        super.setWidth(height);
        super.setHeight(height);
    }
}
