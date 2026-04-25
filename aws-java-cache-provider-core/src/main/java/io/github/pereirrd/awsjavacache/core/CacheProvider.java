package io.github.pereirrd.awsjavacache.core;

import java.time.Duration;

public interface CacheProvider {

    String get(String key);

    void put(String key, String value);

    /**
     * Stores a value with a time-to-live. When {@code ttl} is null, zero, or negative, behaviour matches
     * {@link #put(String, String)} (no expiry where the engine supports it).
     */
    void put(String key, String value, Duration ttl);

    void remove(String key);

    void clear();

    void close();

    void flush();

    void invalidate(String key);
}
