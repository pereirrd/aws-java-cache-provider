package io.github.pereirrd.awsjavacache.config;

import io.github.pereirrd.awsjavacache.util.CacheEnvSupport;
import java.util.Map;

/**
 * Memcached connection settings read from the environment (ElastiCache Memcached with spymemcached).
 * Environment variable names are {@link Keys}.
 */
public record MemcachedCacheEnvConfig(String nodes, long operationTimeoutMillis) {

  /** Default operation timeout aligned with spymemcached {@link net.spy.memcached.ConnectionFactoryBuilder}. */
  public static final long DEFAULT_OPERATION_TIMEOUT_MS = 2500L;

  /** Environment variable names for Memcached (ElastiCache Memcached / spymemcached). */
  public static final class Keys {

    private Keys() {}

    /**
     * Required. Node list as accepted by {@link net.spy.memcached.AddrUtil#getAddresses(String)}, e.g. {@code
     * cache.amazonaws.com:11211} or {@code host1:11211 host2:11211}.
     */
    public static final String NODES = "AWS_JAVA_CACHE_MEMCACHED_NODES";

    /** Optional. Operation timeout in milliseconds. Default {@code 2500}. */
    public static final String OPERATION_TIMEOUT_MS = "AWS_JAVA_CACHE_MEMCACHED_OPERATION_TIMEOUT_MS";
  }

  public static MemcachedCacheEnvConfig fromEnvironment() {
    return from(System.getenv());
  }

  public static MemcachedCacheEnvConfig from(Map<String, String> env) {
    var nodes = CacheEnvSupport.required(env, Keys.NODES);
    var opTimeout =
        CacheEnvSupport.parseLong(env, Keys.OPERATION_TIMEOUT_MS, DEFAULT_OPERATION_TIMEOUT_MS);

    if (opTimeout <= 0) {
      throw new IllegalArgumentException(Keys.OPERATION_TIMEOUT_MS + " must be positive when set");
    }

    return new MemcachedCacheEnvConfig(nodes, opTimeout);
  }
}
