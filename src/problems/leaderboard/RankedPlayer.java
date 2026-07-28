package problems.leaderboard;

/**
 * What the API actually returns: a player, their score, and their position.
 *
 * <p>A separate type from {@link Entry} on purpose. {@code Entry} is internal storage and carries
 * {@code sequence}, an implementation detail of tie-breaking that no caller should ever see or
 * depend on. {@code RankedPlayer} is the wire shape — this is the DTO boundary, and keeping the two
 * apart is what lets you change the tie-break rule later without breaking a single client.
 *
 * @param rank     1-based position on the board
 * @param playerId the player
 * @param score    their score at the time the board was read
 */
public record RankedPlayer(long rank, String playerId, long score) {

    @Override
    public String toString() {
        return "#" + rank + " " + playerId + " (" + score + ")";
    }
}
