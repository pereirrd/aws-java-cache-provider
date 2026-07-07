package io.github.pereirrd.awsjavacache.api.metrics;

import java.time.Duration;

/**
 * Optional observability hooks for cache strategy services. Implementations should be thread-safe when invoked from
 * concurrent callers.
 *
 * <p>Latency values cover the cache or origin operation that triggered the callback (not the full end-to-end request
 * unless noted by the strategy).
 */
public interface CacheMetrics {

    CacheMetrics NO_OP = new CacheMetrics() {};

    default void onCacheHit(String cacheKey, Duration latency) {}

    default void onCacheMiss(String cacheKey, Duration latency) {}

    default void onOriginLoad(String cacheKey, Duration latency) {}

    default void onCachePut(String cacheKey, Duration latency) {}

    default void onCacheEvict(String cacheKey) {}
}
