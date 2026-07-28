package problems.tictactoe;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The rules: whose turn it is, what is legal, and when it is over.
 *
 * <p><b>Why this class exists separately from {@link Board}.</b> The board knows about geometry and
 * nothing else — where marks are and whether a line is complete. The game knows about players, turn
 * order and legality. Fusing them is the most common structural mistake on this problem, and it bites
 * immediately: the moment you want to test win detection on a 1000 x 1000 grid you would have to
 * invent two players and take 999,999 turns to get there.
 *
 * <p><b>The four illegal moves worth rejecting explicitly</b>, because interviewers probe for them
 * and each one is a single guard:
 * <ol>
 *   <li>outside the board,</li>
 *   <li>a cell that is already taken,</li>
 *   <li>a player moving out of turn,</li>
 *   <li>any move at all after the game has ended.</li>
 * </ol>
 * The third is the one candidates leave out. An API that takes only {@code (row, col)} and infers the
 * player cannot detect it — the caller is trusted by construction. Taking the player as a parameter
 * and checking costs one line and closes a whole class of bug.
 */
public final class Game {

    /** Just enough to reverse a move. */
    private record Move(Player player, int row, int col) {
    }

    private final Board board;
    private final List<Player> players;
    private final Deque<Move> history = new ArrayDeque<>();

    private int turnIndex;
    private GameStatus status = GameStatus.IN_PROGRESS;
    private Player winner;

    public Game(int boardSize, List<Player> players) {
        Objects.requireNonNull(players, "players");
        if (players.size() < 2) {
            throw new IllegalArgumentException("a game needs at least two players");
        }
        if (players.stream().map(Player::symbol).distinct().count() != players.size()) {
            // Two players sharing a symbol makes the counters meaningless and the winner ambiguous.
            // Cheap to check, impossible to debug from the symptom.
            throw new IllegalArgumentException("every player needs a distinct symbol");
        }
        this.board = new Board(boardSize);
        this.players = List.copyOf(players);
    }

    /**
     * Plays one move.
     *
     * @return the status after the move
     */
    public GameStatus play(Player player, int row, int col) {
        if (status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("game is already " + status);
        }
        Player expected = currentPlayer();
        if (!expected.equals(player)) {
            throw new IllegalArgumentException("it is " + expected.name() + "'s turn, not " + player.name() + "'s");
        }

        boolean won = board.place(row, col, player.symbol());
        history.push(new Move(player, row, col));

        if (won) {
            status = GameStatus.WON;
            winner = player;
        } else if (board.isFull()) {
            // Order matters: a move can fill the final cell AND win. Checking the draw first would
            // report a drawn game that somebody actually won.
            status = GameStatus.DRAWN;
        } else {
            turnIndex = (turnIndex + 1) % players.size();
        }
        return status;
    }

    /**
     * Reverses the last move, including a game-ending one.
     *
     * <p>The subtlety: {@code play} does not advance the turn on a winning or drawing move, so
     * {@code undo} must not rewind it either. Restoring the turn unconditionally is the bug — undo a
     * win and suddenly the wrong player is up.
     */
    public boolean undo() {
        Move last = history.poll();
        if (last == null) {
            return false;
        }
        board.undo(last.row(), last.col());
        if (status == GameStatus.IN_PROGRESS) {
            turnIndex = (turnIndex - 1 + players.size()) % players.size();
        }
        status = GameStatus.IN_PROGRESS;
        winner = null;
        return true;
    }

    public Player currentPlayer() {
        return players.get(turnIndex);
    }

    public GameStatus status() {
        return status;
    }

    public Optional<Player> winner() {
        return Optional.ofNullable(winner);
    }

    public Board board() {
        return board;
    }
}
