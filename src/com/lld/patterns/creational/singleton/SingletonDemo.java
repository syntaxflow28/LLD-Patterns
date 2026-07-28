package com.lld.patterns.creational.singleton;

/**
 * SINGLETON — guarantee a class has exactly one instance and a global access point.
 *
 * When to use in LLD:
 *   - Shared, stateless-ish services: configuration, logger, connection pool, in-memory cache,
 *     the single ParkingLot / VendingMachine controller.
 *
 * Watch-outs (say these in an interview):
 *   - Thread safety: a naive lazy singleton is not thread-safe.
 *   - Testability: global state makes mocking hard; prefer dependency injection where possible.
 *
 * This file shows the two idioms you should know.
 */

// 1) Bill Pugh / holder idiom — lazy, thread-safe, no synchronization cost. Preferred in most cases.
class ConfigService {
    private ConfigService() { }                 // private ctor blocks external instantiation

    private static class Holder {               // class loaded only on first getInstance() call
        private static final ConfigService INSTANCE = new ConfigService();
    }

    static ConfigService getInstance() {
        return Holder.INSTANCE;                  // JVM guarantees lazy, thread-safe class init
    }

    String get(String key) { return "value-of-" + key; }
}

// 2) Double-checked locking — use when you need lazy init but with explicit control.
class ConnectionPool {
    private static volatile ConnectionPool instance;   // volatile: visibility + prevents reordering
    private ConnectionPool() { }

    static ConnectionPool getInstance() {
        if (instance == null) {                        // first check (no lock)
            synchronized (ConnectionPool.class) {
                if (instance == null) {                 // second check (with lock)
                    instance = new ConnectionPool();
                }
            }
        }
        return instance;
    }
}

public class SingletonDemo {
    public static void main(String[] args) {
        ConfigService a = ConfigService.getInstance();
        ConfigService b = ConfigService.getInstance();
        System.out.println("Same ConfigService instance? " + (a == b));
        System.out.println("db.url = " + a.get("db.url"));

        ConnectionPool p1 = ConnectionPool.getInstance();
        ConnectionPool p2 = ConnectionPool.getInstance();
        System.out.println("Same ConnectionPool instance? " + (p1 == p2));
    }
}
