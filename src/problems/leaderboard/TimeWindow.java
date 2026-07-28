package problems.leaderboard;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;

/**
 * STRATEGY AS AN ENUM — "daily, weekly and all-time" is a requirement, not a nice-to-have.
 *
 * <p>Almost every leaderboard question includes it, and candidates usually reach for a timestamp
 * filter: store every submission with its time and filter by range at read time. That is the wrong
 * shape and it is worth being able to say why in one sentence — <b>it turns an O(k) read into a scan
 * over the entire submission history, on the hottest query in the product.</b> A daily board with a
 * million submissions would re-aggregate a million rows every time someone opens the app.
 *
 * <p><b>The right shape is bucketing.</b> Each window maps a timestamp to a bucket key, and each
 * bucket is its own pre-aggregated leaderboard. A submission is written to all three; a read touches
 * exactly one and is already sorted. Writes get more expensive (three updates instead of one) and
 * reads get dramatically cheaper — the correct trade for something read far more than written.
 *
 * <p>The second, quieter benefit: <b>expiry becomes free.</b> Deleting yesterday's board is dropping
 * one key, not a range delete across a hot table.
 *
 * <p><b>An enum rather than an interface here</b>, unlike {@link ScoringRule}. The set really is
 * closed — a window either is or is not a calendar period, and the follow-up "add monthly" is one
 * more constant, not a new abstraction. Reach for an interface when the set is open; an enum is
 * simpler and gives you {@code values()} for the fan-out loop.
 *
 * <p><b>Say the timezone out loud.</b> "Daily" is meaningless without one, and this is a real
 * decision an interviewer may probe: UTC keeps every player on the same board (fair, but your daily
 * reset happens at 3am for some players), whereas local time is friendlier but means two players can
 * be on different "today" boards and cannot be compared. UTC is chosen here.
 */
public enum TimeWindow {

    DAILY {
        @Override
        public String bucketKey(Instant at) {
            return LocalDate.ofInstant(at, ZoneOffset.UTC).toString();
        }
    },

    WEEKLY {
        @Override
        public String bucketKey(Instant at) {
            LocalDate date = LocalDate.ofInstant(at, ZoneOffset.UTC);
            // ISO week-based year, not the calendar year: 2026-01-01 belongs to week 1 of 2026, but
            // 2027-01-01 falls in the final ISO week of 2026. Using getYear() here puts two days of
            // the same week on different boards, which is a genuinely nasty New Year bug.
            return date.get(IsoFields.WEEK_BASED_YEAR) + "-W"
                    + String.format("%02d", date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
        }
    },

    ALL_TIME {
        @Override
        public String bucketKey(Instant at) {
            return "all";
        }
    };

    /** @return the identifier of the bucket this instant belongs to, in UTC */
    public abstract String bucketKey(Instant at);
}
