package com.lld.problems.logger;

/**
 * Severity levels, ordered from noisiest to most urgent.
 *
 * <p><b>Why an enum with an explicit severity number instead of relying on {@code ordinal()}.</b>
 * {@code ordinal()} works right up until someone inserts a level in the middle, at which point every
 * persisted or transmitted level silently shifts. Explicit numbers also leave gaps (10, 20, 30...)
 * so a custom level can be slotted in later without renumbering anything — the same trick Python's
 * {@code logging} module and Log4j both use.
 *
 * <p><b>Why comparison lives here.</b> {@link #isAtLeast} keeps the "is this important enough?"
 * rule in one place. The alternative is {@code level.ordinal() >= threshold.ordinal()} sprinkled
 * across the logger, every handler and every appender — four copies of a comparison that is easy to
 * get backwards, and no single place to change if the semantics ever shift.
 */
public enum LogLevel {

    /** Firehose. Method entry/exit, loop internals. Off in production. */
    TRACE(10),

    /** Diagnostic detail useful when investigating. Off in production, on when debugging. */
    DEBUG(20),

    /** Normal, noteworthy events: service started, order placed. The usual production default. */
    INFO(30),

    /** Something recoverable happened: retry succeeded, cache miss storm, deprecated call. */
    WARN(40),

    /** An operation failed. Someone should look, but the process is still healthy. */
    ERROR(50),

    /** The process cannot continue. Wake someone up. */
    FATAL(60);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    /** @return true if this level is as important as, or more important than, {@code threshold} */
    public boolean isAtLeast(LogLevel threshold) {
        return this.severity >= threshold.severity;
    }
}
