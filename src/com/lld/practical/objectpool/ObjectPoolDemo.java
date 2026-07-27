package com.lld.practical.objectpool;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * OBJECT POOL — reuse a fixed set of expensive-to-create objects instead of allocating new ones.
 * Borrow -> use -> return.
 *
 * When to use in LLD:
 *   - DB connection pools, thread pools, socket pools, large buffers, game object recycling.
 *
 * Interview talking points:
 *   - Must be thread-safe (this version synchronizes and blocks when empty).
 *   - Must reset object state on release, or you leak data between users.
 *   - Needs a max size + timeout policy, otherwise callers can block forever.
 */

class DbConnection {
    private final int id;
    private String lastQuery = "";

    DbConnection(int id) {
        this.id = id;
        System.out.println("  (expensive: opened connection " + id + ")");
    }

    void execute(String sql) { this.lastQuery = sql; System.out.println("conn-" + id + " ran: " + sql); }

    /** Clear per-borrow state before the object goes back in the pool. */
    void reset() { this.lastQuery = ""; }

    @Override public String toString() { return "conn-" + id; }
}

class ConnectionPool {
    private final Deque<DbConnection> available = new ArrayDeque<>();
    private final int maxSize;
    private int created = 0;

    ConnectionPool(int maxSize) { this.maxSize = maxSize; }

    /** Blocks if the pool is exhausted; creates lazily up to maxSize. */
    synchronized DbConnection acquire() throws InterruptedException {
        while (available.isEmpty() && created >= maxSize) {
            wait();                                  // all connections are in use
        }
        if (!available.isEmpty()) return available.pop();
        created++;
        return new DbConnection(created);
    }

    synchronized void release(DbConnection conn) {
        conn.reset();                                // critical: no state leaks to the next borrower
        available.push(conn);
        notifyAll();                                 // wake a waiting acquirer
    }
}

public class ObjectPoolDemo {
    public static void main(String[] args) throws InterruptedException {
        ConnectionPool pool = new ConnectionPool(2);

        DbConnection a = pool.acquire();
        DbConnection b = pool.acquire();
        a.execute("SELECT 1");
        b.execute("SELECT 2");

        pool.release(a);

        DbConnection c = pool.acquire();             // reuses the released connection, no new open
        c.execute("SELECT 3");
        System.out.println("Reused the same object? " + (a == c));

        pool.release(b);
        pool.release(c);
    }
}
