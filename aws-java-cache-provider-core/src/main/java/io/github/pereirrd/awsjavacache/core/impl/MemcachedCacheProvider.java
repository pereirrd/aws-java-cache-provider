package io.github.pereirrd.awsjavacache.core.impl;

import io.github.pereirrd.awsjavacache.core.CacheProvider;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.transcoders.SerializingTranscoder;
import net.spy.memcached.transcoders.Transcoder;

public final class MemcachedCacheProvider implements CacheProvider {

    /** Memcached maximum expiration offset in seconds (30 days). */
    private static final int MAX_EXP_SECONDS = 60 * 60 * 24 * 30;

    private final MemcachedClient client;
    private final Transcoder<String> valueTranscoder;

    public MemcachedCacheProvider(MemcachedClient client, Transcoder<String> valueTranscoder) {
        this.client = Objects.requireNonNull(client, "client");
        this.valueTranscoder = Objects.requireNonNull(valueTranscoder, "valueTranscoder");
    }

    public static MemcachedCacheProvider utf8Strings(MemcachedClient client) {
        Objects.requireNonNull(client, "client");
        return new MemcachedCacheProvider(client, stringTranscoder());
    }

    @SuppressWarnings("unchecked")
    private static Transcoder<String> stringTranscoder() {
        return (Transcoder<String>) (Transcoder<?>) new SerializingTranscoder();
    }

    @Override
    public String get(String key) {
        return client.get(key, valueTranscoder);
    }

    @Override
    public void put(String key, String value) {
        await(client.set(key, 0, value, valueTranscoder));
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        var exp = toMemcachedExpirationSeconds(ttl);
        await(client.set(key, exp, value, valueTranscoder));
    }

    @Override
    public void remove(String key) {
        await(client.delete(key));
    }

    @Override
    public void clear() {
        await(client.flush());
    }

    @Override
    public void close() {
        client.shutdown(60, TimeUnit.SECONDS);
    }

    @Override
    public void flush() {
        await(client.flush());
    }

    @Override
    public void invalidate(String key) {
        await(client.delete(key));
    }

    private static int toMemcachedExpirationSeconds(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return 0;
        }
        var seconds = ttl.toSeconds();
        if (seconds < 1L) {
            return 1;
        }
        return (int) Math.min(seconds, MAX_EXP_SECONDS);
    }

    private static void await(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            var cause = e.getCause();
            throw cause instanceof RuntimeException re ? re : new IllegalStateException(cause);
        }
    }
}
