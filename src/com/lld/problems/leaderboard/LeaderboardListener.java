package com.lld.problems.leaderboard;

/**
 * OBSERVER — everything that wants to react to the board without the board knowing it exists.
 *
 * <p>The requirement arrives as "push a notification when someone breaks into the top 10", and the
 * tempting implementation is to call the notification service from inside {@code submit()}. That
 * welds the leaderboard to a push provider, makes it untestable without one, and means an outage in
 * notifications becomes an outage in score submission. Observer keeps the board's only job as
 * ranking.
 *
 * <p><b>Default methods, not abstract ones.</b> A metrics listener cares about every update; a push
 * notifier only cares about the top-N transitions. Defaults let each subscriber implement the one
 * event it wants instead of writing empty bodies for the rest — and, more importantly, adding a
 * seventh event later does not break the six listeners already written.
 *
 * <p><b>The rule the implementation must respect:</b> listeners are notified <em>outside</em> the
 * board's lock, and each one is wrapped in its own try/catch. A slow listener must not hold the
 * write lock and stall every score submission in the system, and a broken listener must not prevent
 * the listeners registered after it from running.
 */
public interface LeaderboardListener {

    /** Fired on every submission, whether or not the player's position changed. */
    default void onScoreUpdated(String board, String playerId, long oldScore, long newScore) {
    }

    /** Fired when a player appears in the tracked top slice they were not in before. */
    default void onEnteredTop(String board, String playerId, long rank) {
    }

    /** Fired when a player is pushed out of the tracked top slice. */
    default void onDisplacedFromTop(String board, String playerId) {
    }
}
