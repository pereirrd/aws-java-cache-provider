package io.github.pereirrd.awsjavacache.service;

import io.github.pereirrd.awsjavacache.core.CacheProvider;
import jakarta.inject.Singleton;
import java.time.Duration;

@Singleton
public class CacheService {

    private final CacheProvider cacheProvider;

    public CacheService(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }

    public String get(String key) {
        return cacheProvider.get(key);
    }

    public void put(String key, String value) {
        cacheProvider.put(key, value);
    }

    public void put(String key, String value, Duration ttl) {
        cacheProvider.put(key, value, ttl);
    }

    public void remove(String key) {
        cacheProvider.remove(key);
    }

    public void clear() {
        cacheProvider.clear();
    }

    public void close() {
        cacheProvider.close();
    }

    public void flush() {
        cacheProvider.flush();
    }

    public void invalidate(String key) {
        cacheProvider.invalidate(key);
    }
}
