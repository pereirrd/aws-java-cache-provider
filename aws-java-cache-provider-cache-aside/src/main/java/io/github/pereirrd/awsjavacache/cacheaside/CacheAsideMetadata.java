package io.github.pereirrd.awsjavacache.cacheaside;

import java.time.Duration;
import java.util.function.Function;

/**
 * Resolved cache-aside configuration for an annotated entity class.
 *
 * @param <ID> identifier type
 */
public record CacheAsideMetadata<ID>(
        String cacheKeyPrefix,
        Function<ID, String> cacheKeyForId,
        Duration entryTtl,
        Function<Object, ID> idExtractor) {}
