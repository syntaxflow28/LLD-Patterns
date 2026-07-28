package com.lld.problems.logger;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * STRATEGY — how a {@link LogMessage} is turned into text.
 *
 * <p>Formatting and destination are two independent axes: you may want JSON to a file and
 * human-readable text to the console, or the reverse. Baking the format into the appender forces
 * one class per combination (M formats x N destinations). Keeping them separate and composing them
 * — as {@link LogHandler.Sink} does — turns M x N into M + N. That is Bridge reasoning applied
 * inside a logging framework.
 *
 * <p><b>Why the console format is not JSON.</b> A human tailing a log wants columns; a log
 * aggregator wants machine-parseable fields. Serving both from one format serves neither well,
 * which is precisely the observation that motivates the strategy.
 */
@FunctionalInterface
public interface LogFormatter {

    String format(LogMessage message);

    /**
     * Fixed-width columns, aligned so the eye can scan the level column.
     *
     * <pre>
     *   2026-07-27T12:00:00Z  INFO   [main] OrderService - order 42 placed
     * </pre>
     */
    final class Simple implements LogFormatter {

        private static final DateTimeFormatter TIMESTAMP =
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

        @Override
        public String format(LogMessage message) {
            StringBuilder line = new StringBuilder()
                    .append(TIMESTAMP.format(message.timestamp()))
                    .append("  ")
                    .append(String.format("%-5s", message.level()))
                    .append(" [").append(message.threadName()).append("] ")
                    .append(message.loggerName())
                    .append(" - ")
                    .append(message.message());

            message.errorIfAny().ifPresent(error ->
                    line.append(" | ").append(error.getClass().getSimpleName())
                            .append(": ").append(error.getMessage()));
            return line.toString();
        }
    }

    /**
     * One JSON object per line, the format every log aggregator expects.
     *
     * <p>Note the escaping. Skipping it is the classic bug: a single quote or newline inside a user
     * supplied message produces a malformed line, the aggregator drops it, and the one log entry you
     * needed for the incident is the one that vanished.
     */
    final class Json implements LogFormatter {

        @Override
        public String format(LogMessage message) {
            StringBuilder json = new StringBuilder("{")
                    .append("\"ts\":\"").append(message.timestamp()).append("\",")
                    .append("\"level\":\"").append(message.level()).append("\",")
                    .append("\"logger\":\"").append(escape(message.loggerName())).append("\",")
                    .append("\"thread\":\"").append(escape(message.threadName())).append("\",")
                    .append("\"msg\":\"").append(escape(message.message())).append("\"");

            message.errorIfAny().ifPresent(error ->
                    json.append(",\"error\":\"").append(escape(error.toString())).append("\""));
            return json.append("}").toString();
        }

        private static String escape(String raw) {
            return raw.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
        }
    }
}
