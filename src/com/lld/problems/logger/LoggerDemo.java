package com.lld.problems.logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable walk-through of the logging framework design.
 *
 * <pre>
 *   java -cp out com.lld.problems.logger.LoggerDemo
 * </pre>
 *
 * <p>Output is routed to in-memory appenders and printed deliberately, so the ordering is
 * deterministic. The real {@link LogAppender.Console} splits INFO to stdout and WARN+ to stderr,
 * which is correct behaviour but interleaves unpredictably in a captured transcript.
 *
 * <p><b>How to structure this answer in an interview.</b> Levels, then the message record, then the
 * handler chain, then formatter and appender as the two independent axes. If you are short on time,
 * the chain plus the "format and destination are separate" insight is the 80%.
 */
public class LoggerDemo {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-07-27T09:15:00Z"), ZoneOffset.UTC);

    public static void main(String[] args) throws Exception {

        section("1. A level threshold suppresses everything below it");
        LogAppender.InMemory buffer = new LogAppender.InMemory();
        LogHandler console = new LogHandler.Sink("console", LogLevel.TRACE, new LogFormatter.Simple(), buffer);
        Logger.configure(console, LogLevel.INFO, FIXED);

        Logger log = Logger.get("OrderService");
        log.trace("entering placeOrder()");
        log.debug("cart has 3 items");
        log.info("order 42 placed");
        log.warn("payment retried once");
        log.error("inventory service timed out");
        log.fatal("cannot reach the database");

        System.out.println("  6 calls made at threshold INFO, " + buffer.count() + " lines emitted:");
        buffer.lines().forEach(line -> System.out.println("      " + line));

        section("2. Chain of Responsibility: one message, several destinations");
        LogAppender.InMemory consoleSink = new LogAppender.InMemory();
        LogAppender.InMemory fileSink = new LogAppender.InMemory();
        LogHandler.Alert pager = new LogHandler.Alert("pager", LogLevel.ERROR);

        LogHandler head = new LogHandler.Sink("console", LogLevel.INFO, new LogFormatter.Simple(), consoleSink);
        head.linkTo(new LogHandler.Sink("file", LogLevel.WARN, new LogFormatter.Json(), fileSink))
                .linkTo(pager);
        Logger.configure(head, LogLevel.TRACE, FIXED);

        Logger chained = Logger.get("PaymentService");
        System.out.println("  chain: console(INFO+) -> file(WARN+) -> pager(ERROR+)");
        System.out.println();
        System.out.println("  log.debug(...)");
        chained.debug("card token refreshed");
        System.out.println("  log.info(...)");
        chained.info("charged 499.00 to card ending 4242");
        System.out.println("  log.warn(...)");
        chained.warn("gateway latency above 2s");
        System.out.println("  log.error(...)");
        chained.error("charge declined by issuer");
        System.out.println();
        System.out.printf("  console received %d (INFO, WARN, ERROR)%n", consoleSink.count());
        System.out.printf("  file    received %d (WARN, ERROR)%n", fileSink.count());
        System.out.printf("  pager   received %d (ERROR)%n", pager.pagesSent());
        System.out.println("  Each link applies its OWN minimum level, and the message is always");
        System.out.println("  forwarded - the broadcast variant of the pattern, not the stop-at-first one.");

        section("3. Same event, two formatters");
        LogMessage event = LogMessage.of(FIXED.instant(), LogLevel.WARN, "AuthService", "3 failed logins for user 88");
        System.out.println("  Simple : " + new LogFormatter.Simple().format(event));
        System.out.println("  Json   : " + new LogFormatter.Json().format(event));
        System.out.println("  Neither formatter knows or cares where the line ends up.");

        section("4. File appender, written and read back");
        Path logFile = Files.createTempFile("lld-logger-", ".log");
        logFile.toFile().deleteOnExit();
        LogAppender.File fileAppender = new LogAppender.File(logFile);
        LogHandler toDisk = new LogHandler.Sink("disk", LogLevel.INFO, new LogFormatter.Simple(), fileAppender);
        Logger.configure(toDisk, LogLevel.INFO, FIXED);

        Logger disk = Logger.get("BatchJob");
        disk.info("started");
        disk.info("processed 1000 rows");
        disk.error("row 1001 rejected");
        fileAppender.close();

        System.out.println("  wrote " + logFile.getFileName());
        readLines(logFile).forEach(line -> System.out.println("      " + line));

        section("5. Exceptions, and why JSON escaping is not optional");
        LogAppender.InMemory jsonSink = new LogAppender.InMemory();
        Logger.configure(new LogHandler.Sink("json", LogLevel.INFO, new LogFormatter.Json(), jsonSink),
                LogLevel.INFO, FIXED);

        Logger risky = Logger.get("ImportService");
        risky.error("row rejected", new IllegalArgumentException("qty must be > 0"));
        risky.warn("user typed a \"quoted\" value\nwith a newline in it");

        jsonSink.lines().forEach(line -> System.out.println("      " + line));
        System.out.println("  Both lines are still valid single-line JSON. Without escaping the second");
        System.out.println("  one breaks the aggregator, and it is always the interesting log that breaks it.");

        section("6. Lazy message construction: the supplier overload");
        AtomicInteger expensiveCalls = new AtomicInteger();
        LogAppender.InMemory lazySink = new LogAppender.InMemory();
        Logger.configure(new LogHandler.Sink("lazy", LogLevel.TRACE, new LogFormatter.Simple(), lazySink),
                LogLevel.INFO, FIXED);
        Logger lazy = Logger.get("ReportService");

        lazy.debug(() -> "report dump: " + expensiveDump(expensiveCalls));
        System.out.println("  threshold INFO,  debug(supplier) called -> supplier invoked "
                + expensiveCalls.get() + " time(s)");

        Logger.setThreshold(LogLevel.DEBUG);
        lazy.debug(() -> "report dump: " + expensiveDump(expensiveCalls));
        System.out.println("  threshold DEBUG, debug(supplier) called -> supplier invoked "
                + expensiveCalls.get() + " time(s)");
        System.out.println("  With the eager String overload the dump would be built and discarded both times.");

        section("7. Runtime reconfiguration during an incident");
        Logger.setThreshold(LogLevel.ERROR);
        lazySink.clear();
        Logger ops = Logger.get("OpsService");
        ops.info("this is dropped");
        ops.error("this gets through");
        System.out.println("  threshold ERROR -> " + lazySink.count() + " line(s) emitted from 2 calls");
        Logger.setThreshold(LogLevel.DEBUG);
        lazySink.clear();
        ops.debug("now visible without a redeploy");
        System.out.println("  threshold DEBUG -> " + lazySink.count() + " line(s) emitted from 1 call");

        section("8. Every thread in the process logs through the same instance");
        LogAppender.InMemory concurrent = new LogAppender.InMemory();
        Logger.configure(new LogHandler.Sink("concurrent", LogLevel.TRACE, new LogFormatter.Simple(), concurrent),
                LogLevel.INFO, FIXED);
        Logger shared = Logger.get("Worker");

        ExecutorService pool = Executors.newFixedThreadPool(4);
        for (int t = 0; t < 4; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 250; i++) {
                    shared.info("tick " + i);
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("  4 threads x 250 messages -> " + concurrent.count() + " lines (expected 1000)");
        System.out.println("  No lost or interleaved lines because the APPENDER is synchronised.");
        System.out.println("  Sample: " + concurrent.lines().get(0));

        Logger.reset();
        System.out.println("\nDone.");
    }

    /** Stands in for something genuinely costly, and counts how often it actually ran. */
    private static String expensiveDump(AtomicInteger counter) {
        counter.incrementAndGet();
        return "<12kb of serialised state>";
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            return List.of("could not read log file: " + e.getMessage());
        }
    }

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
