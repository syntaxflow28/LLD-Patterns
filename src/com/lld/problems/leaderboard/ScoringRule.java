package com.lld.problems.leaderboard;

import java.util.function.LongBinaryOperator;

/**
 * STRATEGY — what does "submitting a score" actually mean?
 *
 * <p>This is the first requirement question worth asking out loud, because the interviewer usually
 * has not specified it and the three answers produce three different products:
 *
 * <ul>
 *   <li><b>ACCUMULATE</b> — add to the running total. Career points, coins collected, XP. A player
 *       who plays more climbs higher.</li>
 *   <li><b>BEST</b> — keep the maximum. Time trials, high scores, single-round tournaments. A player
 *       who plays more gets more <em>chances</em>, but a bad round never hurts them.</li>
 *   <li><b>LATEST</b> — overwrite. Current Elo, current level, current balance. History is
 *       irrelevant; only the present standing matters.</li>
 * </ul>
 *
 * <p><b>Why a Strategy and not an {@code if}.</b> The rule is board-wide and fixed for that board's
 * lifetime, so it is exactly the "one axis, several interchangeable algorithms" shape Strategy
 * exists for. A branch inside {@code submit()} would mean every new rule edits the class that must
 * never break.
 *
 * <p><b>Why an interface and not an enum.</b> An enum would be tempting — the set looks closed. But
 * the follow-up is always "now add decay, where old points lose 10% a week" or "cap the score at
 * 10,000", and those are new implementations rather than new enum constants. An interface keeps the
 * set open; the three constants below are just the defaults everybody starts from.
 */
public interface ScoringRule {

    /**
     * @param currentScore    the player's score before this submission (0 for a new player)
     * @param submittedPoints the points from this submission
     * @return the player's new score
     */
    long combine(long currentScore, long submittedPoints);

    String name();

    /** Small adapter so the three defaults below stay one line each and still report a name. */
    record Named(String name, LongBinaryOperator operator) implements ScoringRule {

        @Override
        public long combine(long currentScore, long submittedPoints) {
            return operator.applyAsLong(currentScore, submittedPoints);
        }
    }

    /** Running total. Career points, XP, coins. */
    ScoringRule ACCUMULATE = new Named("ACCUMULATE", Long::sum);

    /** Personal best. High scores, time trials — a bad round can never lower your standing. */
    ScoringRule BEST = new Named("BEST", Math::max);

    /** Current value. Elo, level, balance — history does not matter. */
    ScoringRule LATEST = new Named("LATEST", (currentScore, submittedPoints) -> submittedPoints);
}
