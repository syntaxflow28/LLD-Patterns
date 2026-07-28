package problems.tictactoe;

/**
 * The marks a player can own.
 *
 * <p>Three constants rather than two, deliberately. The classic implementation stores {@code +1} for
 * X and {@code -1} for O and checks {@code abs(count) == n} — an elegant trick that is worth knowing
 * and worth naming in the interview, but it hard-codes "there are exactly two players" into the data
 * structure. The follow-up "generalise to k players on an n x n board" then costs a rewrite. Indexing
 * counters by {@code ordinal()} is barely more code and generalises for free.
 */
public enum Symbol {

    X('X'),
    O('O'),
    PLUS('+');

    private final char glyph;

    Symbol(char glyph) {
        this.glyph = glyph;
    }

    public char glyph() {
        return glyph;
    }
}
