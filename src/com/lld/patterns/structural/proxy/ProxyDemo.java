package com.lld.patterns.structural.proxy;

import java.util.HashMap;
import java.util.Map;

/**
 * PROXY — provide a surrogate that controls access to another object, exposing the SAME interface.
 * Unlike Decorator (which adds behavior), Proxy's intent is access control.
 *
 * Common proxy flavors: virtual (lazy init), protection (auth), caching, remote, rate-limiting.
 *
 * When to use in LLD:
 *   - Caching results of an expensive service, gating access by permission, lazy-loading heavy
 *     resources, throttling calls.
 *
 * Here: a caching proxy in front of an expensive image/data loader.
 */

interface DataSource {
    String fetch(String key);
}

/** The real, expensive subject. */
class RemoteDataSource implements DataSource {
    public String fetch(String key) {
        System.out.println("...expensive remote fetch for '" + key + "'");
        return "DATA(" + key + ")";
    }
}

/** Caching proxy: same interface, adds a cache in front and delegates on a miss. */
class CachingDataSourceProxy implements DataSource {
    private final DataSource real = new RemoteDataSource();
    private final Map<String, String> cache = new HashMap<>();

    public String fetch(String key) {
        if (cache.containsKey(key)) {
            System.out.println("(cache hit for '" + key + "')");
            return cache.get(key);
        }
        String value = real.fetch(key);   // control access: only reach the real object on a miss
        cache.put(key, value);
        return value;
    }
}

public class ProxyDemo {
    public static void main(String[] args) {
        DataSource ds = new CachingDataSourceProxy();

        System.out.println(ds.fetch("user:1")); // miss -> remote
        System.out.println(ds.fetch("user:1")); // hit  -> cache
        System.out.println(ds.fetch("user:2")); // miss -> remote
    }
}
