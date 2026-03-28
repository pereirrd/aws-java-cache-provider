package io.github.pereirrd.awsjavacache.config;

import io.github.pereirrd.awsjavacache.util.CacheEnvSupport;
import java.util.Map;

public record MemcachedCacheEnvConfig(String nodes, long operationTimeoutMillis) {

    public static final long DEFAULT_OPERATION_TIMEOUT_MS = 2500L;

    public static final class Keys {

        private Keys() {}

        public static final String NODES = "AWS_JAVA_CACHE_MEMCACHED_NODES";

        public static final String OPERATION_TIMEOUT_MS = "AWS_JAVA_CACHE_MEMCACHED_OPERATION_TIMEOUT_MS";
    }

    public static MemcachedCacheEnvConfig fromEnvironment() {
        return from(System.getenv());
    }

    public static MemcachedCacheEnvConfig from(Map<String, String> env) {
        var nodes = CacheEnvSupport.required(env, Keys.NODES);
        var opTimeout = CacheEnvSupport.parseLong(env, Keys.OPERATION_TIMEOUT_MS, DEFAULT_OPERATION_TIMEOUT_MS);

        if (opTimeout <= 0) {
            throw new IllegalArgumentException(Keys.OPERATION_TIMEOUT_MS + " must be positive when set");
        }

        return new MemcachedCacheEnvConfig(nodes, opTimeout);
    }
}
