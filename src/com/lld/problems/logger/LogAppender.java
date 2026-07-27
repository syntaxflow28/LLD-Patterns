package com.lld.problems.logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The destination a formatted line is written to. Console, file, socket, in-memory buffer.
 *
 * <p><b>Why this is a separate interface from {@link LogFormatter}.</b> "What it looks like" and
 * "where it goes" change for different reasons and at different times. Merging them gives you
 * {@code JsonFileAppender}, {@code TextFileAppender}, {@code JsonConsoleAppender}... one class per
 * pairing. Keeping them apart means adding a Syslog destination costs one class and works with
 * every existing format for free.
 *
 * <p><b>Why every implementation is thread-safe.</b> A logger is called from every thread in the
 * process, always. An appender that is not synchronised produces interleaved half-lines under load
 * — and it will look perfectly fine in single-threaded testing. Say this before being asked.
 */
public interface LogAppender {

    /**
     * @param message  the structured event, so the appender can still route on level
     * @param rendered the already-formatted text
     */
    void append(LogMessage message, String rendered);

    /** Most appenders hold nothing; file and socket appenders override this. */
    default void close() {
    }

    /**
     * Standard out for everything, standard error from WARN upward.
     *
     * <p>That split is not cosmetic: it is what lets an operator run {@code app 2> errors.log} or
     * a container platform separate the two streams without parsing anything.
     */
    final class Console implements LogAppender {

        @Override
        public synchronized void append(LogMessage message, String rendered) {
            if (message.level().isAtLeast(LogLevel.WARN)) {
                System.err.println(rendered);
            } else {
                System.out.println(rendered);
            }
        }
    }

    /**
     * Keeps lines in a list. This exists for tests, and it is worth calling that out: the reason
     * appenders are an interface at all is so that a test can assert "exactly one ERROR was logged"
     * without scraping stdout.
     */
    final class InMemory implements LogAppender {

        private final List<String> lines = new ArrayList<>();

        @Override
        public synchronized void append(LogMessage message, String rendered) {
            lines.add(rendered);
        }

        public synchronized List<String> lines() {
            return List.copyOf(lines); // defensive copy: callers must not mutate our buffer
        }

        public synchronized int count() {
            return lines.size();
        }

        public synchronized void clear() {
            lines.clear();
        }
    }

    /**
     * Appends to a file, one line per event.
     *
     * <p><b>Deliberately naive, and you should say so.</b> This opens, writes and closes the file on
     * every single call. A real appender wraps a {@code BufferedWriter} kept open, flushes on a
     * timer or on ERROR, and adds rolling by size or date plus a retention policy — otherwise the
     * disk fills and takes the service down with it. Log rotation is the follow-up question here
     * roughly every time.
     */
    final class File implements LogAppender {

        private final Path path;

        public File(Path path) {
            this.path = path;
        }

        @Override
        public synchronized void append(LogMessage message, String rendered) {
            try {
                Files.writeString(path, rendered + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                // A logging failure must never take down the caller's business logic. Real
                // frameworks swallow this to an internal "status logger"; throwing here would mean
                // a full disk crashes the application.
                throw new UncheckedIOException("could not write log line", e);
            }
        }

        public Path path() {
            return path;
        }
    }
}
