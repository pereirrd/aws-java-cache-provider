package io.github.pereirrd.awsjavacache.config;

import io.github.pereirrd.awsjavacache.util.CacheEnvSupport;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public record RedisCacheEnvConfig(
    String host,
    int port,
    String username,
    String password,
    boolean tls,
    int database,
    Optional<Duration> commandTimeout) {

  public static final class Keys {

    private Keys() {}

    public static final String HOST = "AWS_JAVA_CACHE_REDIS_HOST";

    public static final String PORT = "AWS_JAVA_CACHE_REDIS_PORT";

    public static final String USERNAME = "AWS_JAVA_CACHE_REDIS_USERNAME";

    public static final String PASSWORD = "AWS_JAVA_CACHE_REDIS_PASSWORD";

    public static final String TLS = "AWS_JAVA_CACHE_REDIS_TLS";

    public static final String DATABASE = "AWS_JAVA_CACHE_REDIS_DATABASE";

    public static final String TIMEOUT_MS = "AWS_JAVA_CACHE_REDIS_TIMEOUT_MS";
  }

  public static RedisCacheEnvConfig fromEnvironment() {
    return from(System.getenv());
  }

  public static RedisCacheEnvConfig from(Map<String, String> env) {
    var host = CacheEnvSupport.required(env, Keys.HOST);
    var port = CacheEnvSupport.parseInt(env, Keys.PORT, 6379);
    var username = CacheEnvSupport.optional(env, Keys.USERNAME);
    var password = CacheEnvSupport.optional(env, Keys.PASSWORD);
    var tls = CacheEnvSupport.parseBoolean(env, Keys.TLS, false);
    var database = CacheEnvSupport.parseInt(env, Keys.DATABASE, 0);
    var timeoutMs = CacheEnvSupport.parseLong(env, Keys.TIMEOUT_MS, -1L);
    var commandTimeout =
        timeoutMs < 0 ? Optional.<Duration>empty() : Optional.of(Duration.ofMillis(timeoutMs));

    return new RedisCacheEnvConfig(host, port, username, password, tls, database, commandTimeout);
  }
}
