package problems.tictactoe;

/**
 * A participant. A record, because a player is data — a name and the mark they own — with no
 * behaviour of its own.
 *
 * <p>Resist the urge to give this class a {@code makeMove()} method. That reads well right up until
 * you notice the player would need a reference to the board, the board would need to validate whose
 * turn it is, and the turn rule now lives in two places. Moves belong to {@link Game}, which is the
 * only object that knows the rules.
 *
 * @param name   display name
 * @param symbol the mark this player places
 */
public record Player(String name, Symbol symbol) {

    public Player {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (symbol == null) {
            throw new IllegalArgumentException("symbol is required");
        }
    }

    @Override
    public String toString() {
        return name + " (" + symbol.glyph() + ")";
    }
}
