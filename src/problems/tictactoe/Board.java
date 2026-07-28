package problems.tictactoe;

/**
 * The grid, and the one genuinely interesting algorithm in this problem: <b>O(1) win detection</b>.
 *
 * <p><b>The question that turns a warm-up into a real interview.</b> Everyone can write tic-tac-toe.
 * The follow-up is always "now the board is 1000 x 1000" — and the natural implementation, which
 * re-scans the affected row, column and diagonals after every move, is O(n) per move. At n = 1000
 * that is a million comparisons over a full game for information you already had.
 *
 * <p><b>The insight:</b> a move only ever affects one row, one column and at most two diagonals, and
 * a line is a winner exactly when it holds {@code n} marks of the same symbol. So do not re-derive
 * that by looking — <em>count it as it happens</em>. Four counter arrays, four increments per move,
 * four comparisons, and the board size stops mattering entirely.
 *
 * <pre>
 *   rowCounts[row][symbol]      how many of that symbol are in that row
 *   colCounts[col][symbol]      ... that column
 *   diagCounts[symbol]          ... the main diagonal   (row == col)
 *   antiDiagCounts[symbol]      ... the anti-diagonal   (row + col == n - 1)
 * </pre>
 *
 * <p>Memory is O(n x players) rather than the O(n^2) of the grid itself, so the counters are strictly
 * cheaper than the thing they replace.
 *
 * <p><b>The limit to volunteer.</b> This works because a win means <em>n</em> in a row on an
 * <em>n</em>-wide board, so a full line is the only possibility. Change the rule to "k in a row where
 * k &lt; n" — Connect Four, Gomoku — and counters no longer decide it, because a line can contain a
 * winning run and a gap. That variant needs a bounded scan outward from the last move in four
 * directions, which is O(k) and still independent of n. Knowing which of the two you are being asked
 * for, and saying so, is the point.
 */
public final class Board {

    private final int size;
    private final Symbol[][] cells;

    private final int[][] rowCounts;
    private final int[][] colCounts;
    private final int[] diagCounts;
    private final int[] antiDiagCounts;

    private int movesPlayed;

    public Board(int size) {
        if (size < 1) {
            throw new IllegalArgumentException("board size must be at least 1");
        }
        int symbols = Symbol.values().length;
        this.size = size;
        this.cells = new Symbol[size][size];
        this.rowCounts = new int[size][symbols];
        this.colCounts = new int[size][symbols];
        this.diagCounts = new int[symbols];
        this.antiDiagCounts = new int[symbols];
    }

    /**
     * Places a mark and reports whether it won, in O(1).
     *
     * <p>Note the ordering: every counter is incremented <em>before</em> anything is tested. The
     * tempting version short-circuits — {@code if (++rowCounts[...] == size) return true;} — and it
     * is subtly broken, because a winning row move never increments the diagonal counters, so
     * {@link #undo} would decrement a counter that was never raised. Do all the bookkeeping, then ask
     * the question.
     *
     * @return true if this move completed a line
     */
    public boolean place(int row, int col, Symbol symbol) {
        requireInBounds(row, col);
        if (cells[row][col] != null) {
            throw new IllegalArgumentException(
                    "cell (" + row + "," + col + ") already holds " + cells[row][col]);
        }

        cells[row][col] = symbol;
        movesPlayed++;

        int s = symbol.ordinal();
        boolean onDiagonal = row == col;
        boolean onAntiDiagonal = row + col == size - 1;

        rowCounts[row][s]++;
        colCounts[col][s]++;
        if (onDiagonal) {
            diagCounts[s]++;
        }
        if (onAntiDiagonal) {
            antiDiagCounts[s]++;
        }

        return rowCounts[row][s] == size
                || colCounts[col][s] == size
                || (onDiagonal && diagCounts[s] == size)
                || (onAntiDiagonal && antiDiagCounts[s] == size);
    }

    /**
     * Takes a move back.
     *
     * <p>Almost always asked, and it is nearly free here: incremental counters are reversible by
     * construction. A design that re-scanned to detect wins would have nothing to undo — but it would
     * also have nothing to undo <em>with</em>, so it would have to re-scan again to recompute the
     * status. Incremental state pays twice.
     */
    public void undo(int row, int col) {
        requireInBounds(row, col);
        Symbol symbol = cells[row][col];
        if (symbol == null) {
            throw new IllegalStateException("cell (" + row + "," + col + ") is empty");
        }

        int s = symbol.ordinal();
        rowCounts[row][s]--;
        colCounts[col][s]--;
        if (row == col) {
            diagCounts[s]--;
        }
        if (row + col == size - 1) {
            antiDiagCounts[s]--;
        }

        cells[row][col] = null;
        movesPlayed--;
    }

    public boolean isFull() {
        return movesPlayed == size * size;
    }

    public Symbol at(int row, int col) {
        requireInBounds(row, col);
        return cells[row][col];
    }

    public int size() {
        return size;
    }

    public int movesPlayed() {
        return movesPlayed;
    }

    private void requireInBounds(int row, int col) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            throw new IllegalArgumentException(
                    "(" + row + "," + col + ") is outside a " + size + "x" + size + " board");
        }
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        for (int row = 0; row < size; row++) {
            out.append("      ");
            for (int col = 0; col < size; col++) {
                out.append(cells[row][col] == null ? '.' : cells[row][col].glyph());
                if (col < size - 1) {
                    out.append(' ');
                }
            }
            if (row < size - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }
}
