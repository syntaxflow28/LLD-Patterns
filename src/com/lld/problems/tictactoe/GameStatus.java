package com.lld.problems.tictactoe;

/**
 * Where the game is.
 *
 * <p>A plain enum, not the State pattern — and being able to say <em>why</em> is worth more here than
 * the pattern would be. State earns its keep when each state has genuinely different behaviour for
 * the same operations (see the vending machine, where {@code insertCoin} means four different things).
 * Here there is one operation, {@code play}, and exactly one rule: it is legal in {@code IN_PROGRESS}
 * and illegal otherwise. A three-constant enum and one guard clause is the correct amount of design.
 *
 * <p>Interviewers do ask "should this be the State pattern?" on this problem, and the answer they are
 * listening for is a reasoned no.
 */
public enum GameStatus {

    IN_PROGRESS,
    WON,
    DRAWN
}
