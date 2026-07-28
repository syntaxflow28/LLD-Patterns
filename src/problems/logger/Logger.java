package problems.logger;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * FACADE + FACTORY + SINGLETON — the two-line API the application actually touches.
 *
 * <pre>
 *   Logger log = Logger.get(OrderService.class.getSimpleName());
 *   log.info("order placed");
 * </pre>
 *
 * <p>Everything behind that — levels, handlers, formatters, appenders — is configuration the caller
 * never sees. That is the Facade, and for a logging framework it is non-negotiable: if using the
 * logger requires wiring, people will use {@code System.out.println} instead.
 *
 * <p><b>Why the configuration is static.</b> Logging is genuinely process-global; every class in
 * the application must reach it without having it injected through six constructors. This is one of
 * the few honest uses of global state, and interviewers accept it — <em>if</em> you name the cost
 * unprompted: static mutable state makes tests order-dependent. That is why {@link #reset()}
 * exists, and why {@link #configure} takes a {@link Clock} rather than reading the wall clock.
 *
 * <p><b>Why {@code volatile} on the mutable statics.</b> Configuration is written once by the
 * startup thread and read by every other thread forever. Without {@code volatile} there is no
 * happens-before edge, and a worker thread may keep observing the default chain indefinitely.
 *
 * <p><b>Why loggers are cached per name.</b> Callers ask for a logger in hot code paths and often in
 * static initialisers. Handing back the same instance keeps allocation at zero and means a name
 * always behaves identically. That is Flyweight thinking applied to a factory.
 */
public final class Logger {

    private static final Map<String, Logger> REGISTRY = new ConcurrentHashMap<>();

    private static volatile LogHandler chain = defaultChain();
    private static volatile LogLevel globalThreshold = LogLevel.INFO;
    private static volatile Clock clock = Clock.systemUTC();

    private final String name;

    private Logger(String name) {
        this.name = name;
    }

    /** Factory + cache. {@code computeIfAbsent} keeps this atomic without a lock. */
    public static Logger get(String name) {
        return REGISTRY.computeIfAbsent(name, Logger::new);
    }

    /**
     * Installs a handler chain. Pass the <em>head</em> of the chain.
     *
     * <p>Called once at startup. Nothing stops it being called later — runtime reconfiguration is a
     * genuine feature (turn on DEBUG for ten minutes during an incident without a redeploy).
     */
    public static void configure(LogHandler head, LogLevel threshold, Clock clock) {
        Logger.chain = head;
        Logger.globalThreshold = threshold;
        Logger.clock = clock;
    }

    /** Changes only the threshold. This is the knob operators actually turn during an incident. */
    public static void setThreshold(LogLevel threshold) {
        globalThreshold = threshold;
    }

    public static LogLevel threshold() {
        return globalThreshold;
    }

    /** Restores defaults. Exists purely so tests are not order-dependent. */
    public static void reset() {
        chain = defaultChain();
        globalThreshold = LogLevel.INFO;
        clock = Clock.systemUTC();
        REGISTRY.clear();
    }

    public void trace(String message) {
        log(LogLevel.TRACE, message, null);
    }

    public void debug(String message) {
        log(LogLevel.DEBUG, message, null);
    }

    public void info(String message) {
        log(LogLevel.INFO, message, null);
    }

    public void warn(String message) {
        log(LogLevel.WARN, message, null);
    }

    public void error(String message) {
        log(LogLevel.ERROR, message, null);
    }

    public void error(String message, Throwable error) {
        log(LogLevel.ERROR, message, error);
    }

    public void fatal(String message) {
        log(LogLevel.FATAL, message, null);
    }

    /**
     * Lazy overload for messages that are expensive to build.
     *
     * <pre>
     *   log.debug(() -&gt; "cart contents: " + cart.serialise());   // serialise() never runs at INFO
     * </pre>
     *
     * <p><b>This is the detail that separates people who have used a logger from people who have
     * written one.</b> With the eager {@code String} overload, {@code log.debug("cart: " +
     * cart.serialise())} builds the string on every call and then throws it away because DEBUG is
     * below the threshold. In a hot loop that is real, measurable cost for output nobody sees.
     * Deferring construction behind a {@link Supplier} is why SLF4J has {@code {}} placeholders and
     * why Log4j 2 added the lambda API.
     */
    public void debug(Supplier<String> message) {
        log(LogLevel.DEBUG, message);
    }

    public void trace(Supplier<String> message) {
        log(LogLevel.TRACE, message);
    }

    public void log(LogLevel level, Supplier<String> message) {
        if (!isEnabled(level)) {
            return; // supplier never invoked
        }
        log(level, message.get(), null);
    }

    /**
     * Lets callers guard an expensive block themselves:
     * {@code if (log.isEnabled(DEBUG)) { ...build a report... }}.
     */
    public boolean isEnabled(LogLevel level) {
        return level.isAtLeast(globalThreshold);
    }

    /**
     * The single funnel every other method routes through.
     *
     * <p>The threshold check happens <em>before</em> the {@link LogMessage} is allocated. Building
     * the event first and letting handlers discard it would allocate an object and read the clock
     * for every suppressed TRACE call in the process.
     */
    public void log(LogLevel level, String message, Throwable error) {
        if (!isEnabled(level)) {
            return;
        }
        LogMessage event = new LogMessage(
                clock.instant(), level, name, Thread.currentThread().getName(), message, error);
        chain.handle(event);
    }

    public String name() {
        return name;
    }

    /**
     * Sensible default so a fresh {@code Logger.get("x").info("y")} prints something. A framework
     * that requires configuration before it will do anything at all is a framework people work
     * around.
     */
    private static LogHandler defaultChain() {
        return new LogHandler.Sink("console", LogLevel.TRACE,
                new LogFormatter.Simple(), new LogAppender.Console());
    }
}
