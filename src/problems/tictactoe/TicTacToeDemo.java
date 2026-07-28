package problems.tictactoe;

import java.util.List;

/**
 * TIC-TAC-TOE — the 45-minute warm-up, and what to do with the other 30 minutes.
 *
 * <p><b>Why this gets asked so often.</b> It is small enough that everyone finishes, which means it
 * discriminates on something other than whether you finished. Two things separate answers:
 * <b>knowing when to stop designing</b>, and <b>having something real to say when the board becomes
 * 1000 x 1000</b>.
 *
 * <p><b>The 45-minute budget.</b>
 * <pre>
 *   0-5    Clarify. n x n or fixed 3 x 3? Two players or k? Undo? Is a win always a full line?
 *   5-12   Types and the API: Symbol, Player, GameStatus, Board.place, Game.play.
 *  12-25   Board with O(1) win detection. This is the part they are actually marking.
 *  25-35   Game: turn order, the four illegal moves, draw-vs-win ordering.
 *  35-45   Follow-ups out loud: undo, k-in-a-row, a bot, persistence. Code whichever they pick.
 * </pre>
 *
 * <p><b>The restraint trap.</b> This problem punishes pattern enthusiasm. A {@code MoveStrategy}, a
 * {@code BoardFactory}, a State machine for three statuses and an Observer for a two-player game are
 * all abstraction with no second implementation behind it, and they cost the minutes you needed for
 * the O(1) win check. The design here is: one enum, one record, one algorithm class, one rules class.
 * Being able to explain <em>why you did not</em> reach for State is a stronger signal than reaching
 * for it.
 *
 * <p>Where patterns genuinely arrive on this problem is the follow-up: a computer opponent is a real
 * <b>Strategy</b> (random / greedy / minimax are three interchangeable algorithms), and undo is
 * naturally <b>Command</b> or <b>Memento</b>. Name them then, not before.
 */
public class TicTacToeDemo {

    public static void main(String[] args) {
        Player priya = new Player("Priya", Symbol.X);
        Player rahul = new Player("Rahul", Symbol.O);

        section("1. The 45 minute budget");
        System.out.println("""
                    0-05  clarify: n x n or 3 x 3? two players or k? undo? is a win a full line?
                    05-12 model:   Symbol, Player, GameStatus, Board.place, Game.play
                    12-25 code:    Board with O(1) win detection - the part actually being marked
                    25-35 code:    turn order, the four illegal moves, draw-vs-win ordering
                    35-45 talk:    undo, k-in-a-row, a bot. Code whichever one they pick.

                  This problem finishes early on purpose, so it discriminates on judgement rather
                  than on speed. The two things that separate answers: knowing when to stop
                  designing, and having something real to say when the board becomes 1000 x 1000.\
                """);

        section("2. A game, start to finish");
        Game game = new Game(3, List.of(priya, rahul));
        int[][] moves = {{0, 0}, {0, 1}, {1, 1}, {0, 2}, {2, 2}};
        for (int[] move : moves) {
            Player mover = game.currentPlayer();
            GameStatus status = game.play(mover, move[0], move[1]);
            System.out.printf("  %-14s plays (%d,%d) -> %s%n", mover, move[0], move[1], status);
        }
        System.out.println(game.board());
        System.out.println("  winner: " + game.winner().orElseThrow());
        System.out.println("  Won on the main diagonal, detected by a counter reaching 3 - no scan.");

        section("3. Why O(1) win detection is the whole question");
        System.out.println("  A move touches exactly one row, one column and at most two diagonals, so");
        System.out.println("  the obvious check re-scans those lines: O(n) per move. Counters make it O(1)");
        System.out.println("  by counting as marks land instead of looking afterwards.");
        System.out.println();
        benchmark(2000);
        System.out.println();
        System.out.println("  Both approaches are instant on a 3x3 board, which is exactly why the");
        System.out.println("  interviewer changes the board size. Counters also cost LESS memory than the");
        System.out.println("  grid they replace: O(n x players) against the grid's O(n^2).");

        section("4. A draw is not a loss for the last player");
        Game drawn = new Game(3, List.of(priya, rahul));
        int[][] drawMoves = {{0, 0}, {0, 1}, {0, 2}, {1, 1}, {1, 0}, {1, 2}, {2, 1}, {2, 0}, {2, 2}};
        for (int[] move : drawMoves) {
            drawn.play(drawn.currentPlayer(), move[0], move[1]);
        }
        System.out.println(drawn.board());
        System.out.println("  status: " + drawn.status() + ", moves played: " + drawn.board().movesPlayed());
        System.out.println("  The final move fills the board AND could have won, so win is tested first.");
        System.out.println("  Checking 'is the board full?' before 'did that win?' reports a drawn game");
        System.out.println("  that somebody actually won - a one-line ordering bug that is easy to miss.");

        section("5. The four illegal moves");
        Game guarded = new Game(3, List.of(priya, rahul));
        guarded.play(priya, 1, 1);

        reject("off the board", () -> guarded.play(rahul, 3, 0));
        reject("cell already taken", () -> guarded.play(rahul, 1, 1));
        reject("out of turn", () -> guarded.play(priya, 0, 0));

        Game finished = new Game(2, List.of(priya, rahul));
        finished.play(priya, 0, 0);
        finished.play(rahul, 1, 0);
        finished.play(priya, 0, 1);
        reject("game already over (" + finished.status() + ")", () -> finished.play(rahul, 1, 1));
        System.out.println("  'Out of turn' is the one candidates omit. An API of play(row, col) that");
        System.out.println("  infers the player CANNOT detect it - the caller is trusted by construction.");
        System.out.println("  Taking the player as a parameter costs one line and closes the whole class.");

        section("6. Undo");
        Game reversible = new Game(3, List.of(priya, rahul));
        reversible.play(priya, 0, 0);
        reversible.play(rahul, 1, 1);
        reversible.play(priya, 0, 1);
        System.out.println("  after 3 moves, next up: " + reversible.currentPlayer());
        reversible.undo();
        System.out.println("  after undo,   next up: " + reversible.currentPlayer() + "  (Priya replays)");
        System.out.println("  Counters are incremental, so undoing is decrementing - nothing recomputes.");
        System.out.println("  Subtlety: play() does not advance the turn on a winning move, so undo()");
        System.out.println("  must not rewind it either, or undoing a win puts the wrong player up.");

        section("7. Generalising costs nothing");
        Player meera = new Player("Meera", Symbol.PLUS);
        Game threeWay = new Game(4, List.of(priya, rahul, meera));
        int[][] wide = {{0, 0}, {1, 0}, {2, 0}, {0, 1}, {1, 1}, {2, 1}, {0, 2}, {1, 2}, {2, 2}, {0, 3}};
        GameStatus status = GameStatus.IN_PROGRESS;
        for (int[] move : wide) {
            status = threeWay.play(threeWay.currentPlayer(), move[0], move[1]);
        }
        System.out.println(threeWay.board());
        System.out.println("  4x4, three players, status: " + status
                + ", winner: " + threeWay.winner().map(Player::toString).orElse("none yet"));
        System.out.println("  Counters indexed by symbol handle k players for free. The famous +1/-1");
        System.out.println("  trick (store X as +1, O as -1, win when abs(count) == n) is neater for two");
        System.out.println("  players and hard-codes 'two' into the data structure. Know both; say which.");

        section("8. What you would NOT build, and what you would");
        System.out.println("  Do not build: a MoveStrategy, a BoardFactory, State classes for three");
        System.out.println("  statuses, or an Observer for a two-player game. Each is an abstraction with");
        System.out.println("  no second implementation behind it, and each costs minutes you needed for");
        System.out.println("  the win check. This problem marks restraint.");
        System.out.println();
        System.out.println("  Patterns arrive with the follow-ups, and then they are real:");
        System.out.println("    - 'Add a computer opponent'   -> Strategy: random, greedy, minimax.");
        System.out.println("    - 'Add undo'                  -> Command (store the inverse) or Memento.");
        System.out.println("    - 'k in a row, not n'         -> counters no longer decide it; scan four");
        System.out.println("                                     directions from the last move, O(k).");
        System.out.println("    - 'Play over a network'       -> the turn check above is now a security");
        System.out.println("                                     boundary, not a convenience.");

        System.out.println("\nDone.");
    }

    /**
     * Counter-based O(1) detection against the O(n) line scan a good candidate writes first.
     *
     * <p>Marks are placed along a single row on purpose. Scattered marks make the scan look free
     * because it aborts on the first cell that does not match — which is exactly how a naive
     * benchmark talks you out of the right data structure. The interesting case is the one that
     * actually happens in a real game: a line filling up, where the scan re-walks a little further
     * every single move and the total cost is quadratic.
     */
    private static void benchmark(int size) {
        Board board = new Board(size);

        long counterNanos = 0;
        long scanNanos = 0;
        boolean agreedEveryMove = true;

        for (int col = 0; col < size; col++) {
            long start = System.nanoTime();
            boolean counterSaysWon = board.place(0, col, Symbol.X);
            counterNanos += System.nanoTime() - start;

            start = System.nanoTime();
            boolean scanSaysWon = scanForWin(board, 0, col, Symbol.X);
            scanNanos += System.nanoTime() - start;

            agreedEveryMove &= counterSaysWon == scanSaysWon;
        }

        System.out.printf("      %d x %d board, %d moves filling one row%n", size, size, size);
        System.out.printf("      counters   (O(1) per move, O(n) total)    %7.1f ms%n", counterNanos / 1_000_000.0);
        System.out.printf("      line scan  (O(n) per move, O(n^2) total)  %7.1f ms%n", scanNanos / 1_000_000.0);
        System.out.println("      identical verdict on every move: " + agreedEveryMove);
    }

    /** The natural implementation: re-derive the answer by looking at the affected lines. */
    private static boolean scanForWin(Board board, int row, int col, Symbol symbol) {
        int size = board.size();

        boolean full = true;
        for (int c = 0; c < size && full; c++) {
            full = board.at(row, c) == symbol;
        }
        if (full) {
            return true;
        }

        full = true;
        for (int r = 0; r < size && full; r++) {
            full = board.at(r, col) == symbol;
        }
        if (full) {
            return true;
        }

        if (row == col) {
            full = true;
            for (int i = 0; i < size && full; i++) {
                full = board.at(i, i) == symbol;
            }
            if (full) {
                return true;
            }
        }

        if (row + col == size - 1) {
            full = true;
            for (int i = 0; i < size && full; i++) {
                full = board.at(i, size - 1 - i) == symbol;
            }
            return full;
        }
        return false;
    }

    private static void reject(String label, Runnable illegalMove) {
        try {
            illegalMove.run();
            System.out.println("      UNEXPECTED: " + label + " was allowed");
        } catch (RuntimeException expected) {
            System.out.printf("      rejected %-28s %s%n", label + ":", expected.getMessage());
        }
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
