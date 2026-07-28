package com.lld.patterns.practical.nullobject;

import java.util.Map;

/**
 * NULL OBJECT — provide a do-nothing implementation of an interface instead of returning null.
 * Removes null checks from every call site and prevents NullPointerExceptions.
 *
 * When to use in LLD:
 *   - Optional collaborators: a logger that may be disabled, a "guest" user with no permissions,
 *     a no-op notification channel, a default/absent config.
 *
 * Caution: don't use it to silently swallow genuine errors — only where "do nothing" is a valid,
 * expected behaviour.
 */

interface Logger {
    void log(String message);
}

class ConsoleLogger implements Logger {
    public void log(String message) { System.out.println("[LOG] " + message); }
}

/** The null object: same interface, harmless behaviour. Often a singleton since it's stateless. */
class NoOpLogger implements Logger {
    static final NoOpLogger INSTANCE = new NoOpLogger();
    private NoOpLogger() { }
    public void log(String message) { /* intentionally does nothing */ }
}

class LoggerFactory {
    private static final Map<String, Logger> LOGGERS = Map.of("payments", new ConsoleLogger());

    /** Never returns null -> callers never need a null check. */
    static Logger forModule(String module) {
        return LOGGERS.getOrDefault(module, NoOpLogger.INSTANCE);
    }
}

class PaymentService {
    private final Logger logger;
    PaymentService(Logger logger) { this.logger = logger; }

    void charge(double amount) {
        logger.log("charging " + amount);   // no `if (logger != null)` clutter anywhere
        System.out.println("Charged $" + amount);
    }
}

public class NullObjectDemo {
    public static void main(String[] args) {
        new PaymentService(LoggerFactory.forModule("payments")).charge(50); // real logger
        new PaymentService(LoggerFactory.forModule("analytics")).charge(75); // silent no-op logger
    }
}
