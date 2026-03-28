package io.github.pereirrd.awsjavacache.core.impl;

import io.github.pereirrd.awsjavacache.core.CacheProvider;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.transcoders.SerializingTranscoder;
import net.spy.memcached.transcoders.Transcoder;

public final class MemcachedCacheProvider implements CacheProvider {

    private final MemcachedClient client;
    private final Transcoder<String> valueTranscoder;
    private final int expirationSeconds;

    public MemcachedCacheProvider(MemcachedClient client, Transcoder<String> valueTranscoder, int expirationSeconds) {
        this.client = Objects.requireNonNull(client, "client");
        this.valueTranscoder = Objects.requireNonNull(valueTranscoder, "valueTranscoder");
        this.expirationSeconds = expirationSeconds;
    }

    public static MemcachedCacheProvider utf8Strings(MemcachedClient client) {
        Objects.requireNonNull(client, "client");
        return new MemcachedCacheProvider(client, stringTranscoder(), 0);
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
        await(client.set(key, expirationSeconds, value, valueTranscoder));
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
