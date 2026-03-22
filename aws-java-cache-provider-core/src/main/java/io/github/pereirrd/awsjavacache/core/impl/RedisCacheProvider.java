package io.github.pereirrd.awsjavacache.core.impl;

import io.github.pereirrd.awsjavacache.core.CacheProvider;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.Objects;

/**
 * {@link CacheProvider} backed by Redis (Lettuce), using the Redis STRING commands (GET / SET / DEL).
 *
 * <p>Use {@link #utf8Strings(RedisClient)} for a connection with the default string codec, or pass a {@link
 * StatefulRedisConnection} for {@code String} keys and values.
 */
public final class RedisCacheProvider implements CacheProvider {

  private final StatefulRedisConnection<String, String> connection;
  private final RedisCommands<String, String> commands;

  public RedisCacheProvider(StatefulRedisConnection<String, String> connection) {
    this.connection = Objects.requireNonNull(connection, "connection");
    this.commands = connection.sync();
  }

  /** Conexão UTF-8 string keys/values via {@link RedisClient#connect()}. */
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
