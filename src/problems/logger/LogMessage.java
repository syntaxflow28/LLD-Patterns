package problems.logger;

import java.time.Instant;
import java.util.Optional;

/**
 * One immutable log event, travelling down the handler chain.
 *
 * <p><b>Why a record and not a String.</b> The moment you format at the call site
 * ({@code log("user " + id + " failed")}) you have thrown away structure: nothing downstream can
 * filter by logger, route by level, or emit JSON, because it only has prose. Carrying the fields
 * and formatting at the very last moment is what lets {@link LogFormatter} exist at all — the same
 * reason modern logging is called <em>structured</em> logging.
 *
 * <p><b>Why immutable.</b> This object is handed to several handlers, possibly on other threads,
 * and possibly buffered for later. If any handler could mutate it, the second handler would see
 * something different from the first. Immutability makes the fan-out safe with zero locking.
 *
 * <p><b>Why the thread name is captured here, at creation.</b> If an appender reads
 * {@code Thread.currentThread()} instead, it reports the <em>appender's</em> thread, which for any
 * asynchronous appender is a background worker — and the field becomes useless exactly when you
 * need it most. Capture context at the call site; render it wherever.
 */
public record LogMessage(
        Instant timestamp,
        LogLevel level,
        String loggerName,
        String threadName,
        String message,
        Throwable error) {

    /** Convenience factory for the common case with no exception attached. */
    public static LogMessage of(Instant timestamp, LogLevel level, String loggerName, String message) {
        return new LogMessage(timestamp, level, loggerName, Thread.currentThread().getName(), message, null);
    }

    /** Optional rather than a nullable getter, so callers cannot forget the null check. */
    public Optional<Throwable> errorIfAny() {
        return Optional.ofNullable(error);
    }
}
