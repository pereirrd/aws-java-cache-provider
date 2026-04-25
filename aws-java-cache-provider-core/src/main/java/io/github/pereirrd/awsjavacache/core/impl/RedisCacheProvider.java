package io.github.pereirrd.awsjavacache.core.impl;

import io.github.pereirrd.awsjavacache.core.CacheProvider;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.time.Duration;
import java.util.Objects;

public final class RedisCacheProvider implements CacheProvider {

    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> commands;

    public RedisCacheProvider(StatefulRedisConnection<String, String> connection) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.commands = connection.sync();
    }

    public static RedisCacheProvider utf8Strings(RedisClient redisClient) {
        Objects.requireNonNull(redisClient, "redisClient");
        return new RedisCacheProvider(redisClient.connect());
    }

    @Override
    public String get(String key) {
        return commands.get(key);
    }

    @Override
    public void put(String key, String value) {
        commands.set(key, value);
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            put(key, value);
            return;
        }
        var millis = ttl.toMillis();
        if (millis < 1000L) {
            commands.set(key, value, SetArgs.Builder.px(Math.max(1L, millis)));
        } else {
            commands.set(key, value, SetArgs.Builder.ex(ttl.toSeconds()));
        }
    }

    @Override
    public void remove(String key) {
        commands.del(key);
    }

    @Override
    public void clear() {
        commands.flushdb();
    }

    @Override
    public void close() {
        connection.close();
    }

    @Override
    public void flush() {
        commands.flushdb();
    }

    @Override
    public void invalidate(String key) {
        commands.del(key);
    }
}
