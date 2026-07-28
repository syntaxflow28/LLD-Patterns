package problems.logger;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * CHAIN OF RESPONSIBILITY — every message walks the chain; each link decides for itself.
 *
 * <p>This is the pattern the logger question is really testing. The naive design is a single
 * {@code if/else if/else} inside {@code log()} deciding where each level goes, which means the
 * class has to be edited every time a destination is added — a direct Open/Closed violation. Here
 * links are assembled at configuration time and the logger knows about exactly one of them.
 *
 * <p><b>The variant matters, and candidates get this wrong.</b> Textbook Chain of Responsibility
 * stops at the first link that handles the request (an approval workflow: one person signs off). A
 * logger uses the <b>broadcast</b> variant: an ERROR should reach the console <em>and</em> the file
 * <em>and</em> the alerting system. So {@link #handle} always forwards. If an interviewer expects
 * the stopping variant, name the difference out loud — knowing there are two shapes is worth more
 * than picking either one.
 *
 * <p><b>TEMPLATE METHOD is also here.</b> {@link #handle} is {@code final}: the routing rule
 * ("act if the level qualifies, then always pass it on") is fixed, and only {@link #write} varies.
 * If subclasses could override {@code handle}, one of them would eventually forget to forward and
 * silently break every handler downstream of it — a bug that shows up as "we stopped getting
 * alerts" weeks later.
 *
 * <p><b>Why each link has its own minimum level.</b> Console at INFO, file at WARN, pager at ERROR
 * is the arrangement almost every real service uses. One global threshold cannot express it.
 */
public abstract class LogHandler {

    private final LogLevel minimumLevel;
    private final String name;
    private LogHandler next;

    protected LogHandler(String name, LogLevel minimumLevel) {
        this.name = name;
        this.minimumLevel = minimumLevel;
    }

    /**
     * Appends a link and returns it, so a chain reads left to right:
     * <pre>
     *   console.linkTo(file).linkTo(pager);
     * </pre>
     * Returning the <em>new</em> link rather than {@code this} is what makes that chaining work. If
     * you want the head back for storing, keep a separate reference — which is exactly what
     * {@link Logger#configure} does.
     */
    public LogHandler linkTo(LogHandler next) {
        this.next = next;
        return next;
    }

    /** Template Method: the routing rule is fixed here; subclasses only supply the action. */
    public final void handle(LogMessage message) {
        if (message.level().isAtLeast(minimumLevel)) {
            write(message);
        }
        if (next != null) {
            next.handle(message); // broadcast: never stop early, other sinks may still want it
        }
    }

    protected abstract void write(LogMessage message);

    public String name() {
        return name;
    }

    public LogLevel minimumLevel() {
        return minimumLevel;
    }

    /**
     * The workhorse link: format with a strategy, write to an appender.
     *
     * <p>Because format and destination are both injected, this single class covers "text to
     * console", "JSON to file", "JSON to a test buffer" and every other pairing. That is the M + N
     * instead of M x N payoff of splitting the two interfaces.
     */
    public static final class Sink extends LogHandler {

        private final LogFormatter formatter;
        private final LogAppender appender;

        public Sink(String name, LogLevel minimumLevel, LogFormatter formatter, LogAppender appender) {
            super(name, minimumLevel);
            this.formatter = formatter;
            this.appender = appender;
        }

        @Override
        protected void write(LogMessage message) {
            appender.append(message, formatter.format(message));
        }
    }

    /**
     * A link that is not a sink: it pages the on-call engineer.
     *
     * <p>Included to make the point that the chain is not just "a list of appenders with levels".
     * Once handlers are first-class you can drop in links that sample, deduplicate, enrich with a
     * trace id, or in this case escalate — none of which the logger needs to know about.
     */
    public static final class Alert extends LogHandler {

        private final AtomicInteger pagesSent = new AtomicInteger();

        public Alert(String name, LogLevel minimumLevel) {
            super(name, minimumLevel);
        }

        @Override
        protected void write(LogMessage message) {
            pagesSent.incrementAndGet();
            System.out.println("      [PAGER] on-call notified: " + message.message());
        }

        public int pagesSent() {
            return pagesSent.get();
        }
    }
}
