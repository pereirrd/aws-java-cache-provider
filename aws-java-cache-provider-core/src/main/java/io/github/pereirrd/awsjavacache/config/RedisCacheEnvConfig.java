package io.github.pereirrd.awsjavacache.config;

import io.github.pereirrd.awsjavacache.util.CacheEnvSupport;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Redis connection settings read from the environment (ElastiCache Redis with Lettuce). Environment variable
 * names are {@link Keys}.
 */
public record RedisCacheEnvConfig(
    String host,
    int port,
    String username,
    String password,
    boolean tls,
    int database,
    Optional<Duration> commandTimeout) {

  /** Environment variable names for Redis (ElastiCache Redis / Lettuce). */
  public static final class Keys {

    private Keys() {}

    /** Required. Primary endpoint hostname (or discovery endpoint for cluster mode at the connection layer). */
    public static final String HOST = "AWS_JAVA_CACHE_REDIS_HOST";

    /** Optional. Default {@code 6379}. */
    public static final String PORT = "AWS_JAVA_CACHE_REDIS_PORT";

    /** Optional. Redis ACL username (Redis 6+). */
    public static final String USERNAME = "AWS_JAVA_CACHE_REDIS_USERNAME";

    /** Optional. Password or secret when auth is enabled. */
    public static final String PASSWORD = "AWS_JAVA_CACHE_REDIS_PASSWORD";

    /** Optional. Default {@code false}. Use {@code true} for in-transit encryption (TLS). */
    public static final String TLS = "AWS_JAVA_CACHE_REDIS_TLS";

    /** Optional. Logical database index. Default {@code 0}. */
    public static final String DATABASE = "AWS_JAVA_CACHE_REDIS_DATABASE";

    /**
     * Optional. Command timeout in milliseconds (Lettuce {@link io.lettuce.core.RedisURI} timeout). If unset,
     * Lettuce defaults apply.
     */
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
