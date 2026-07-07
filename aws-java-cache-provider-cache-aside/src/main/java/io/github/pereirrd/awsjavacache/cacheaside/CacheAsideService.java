package io.github.pereirrd.awsjavacache.cacheaside;

import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_ID_REQUIRED;
import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_KEY_REQUIRED;
import static io.github.pereirrd.awsjavacache.util.CacheMetricsSupport.elapsedSince;

import io.github.pereirrd.awsjavacache.api.metrics.CacheMetrics;
import io.github.pereirrd.awsjavacache.api.repository.BackingRepository;
import io.github.pereirrd.awsjavacache.api.serialization.CacheValueSerializer;
import io.github.pereirrd.awsjavacache.core.CacheProvider;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Cache-aside reads: cache first, then {@link BackingRepository#findById(Object)} on miss, then populate cache.
 * Writes to the origin remain the application's responsibility; use {@link #evict(Object)} or {@link #putCached(Object, Object)}
 * after mutations.
 *
 * @param <ID> identifier type
 * @param <M> model type stored in cache and repository
 */
public final class CacheAsideService<ID, M> {

    private final CacheProvider cacheProvider;
    private final BackingRepository<ID, M> repository;
    private final Function<ID, String> cacheKeyForId;
    private final CacheValueSerializer<M> serializer;
    private final Duration entryTtl;
    private final CacheMetrics metrics;

    public CacheAsideService(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Function<ID, String> cacheKeyForId,
            CacheValueSerializer<M> serializer,
            Duration entryTtl) {
        this(cacheProvider, repository, cacheKeyForId, serializer, entryTtl, CacheMetrics.NO_OP);
    }

    public CacheAsideService(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Function<ID, String> cacheKeyForId,
            CacheValueSerializer<M> serializer,
            Duration entryTtl,
            CacheMetrics metrics) {
        this.cacheProvider = Objects.requireNonNull(cacheProvider, "cacheProvider");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cacheKeyForId = Objects.requireNonNull(cacheKeyForId, "cacheKeyForId");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.entryTtl = Objects.requireNonNull(entryTtl, "entryTtl");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /**
     * Builds a cache-aside service from annotations on {@code entityClass} ({@code @CacheRegion},
     * {@code @CacheKey}, {@code @CacheTtl}, {@code @CacheId} / {@code jakarta.persistence.Id}).
     */
    @SuppressWarnings("unchecked")
    public static <ID, M> CacheAsideService<ID, M> fromAnnotations(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Class<M> entityClass,
            CacheValueSerializer<M> serializer) {
        var metadata = (CacheAsideMetadata<ID>) CacheAsideAnnotationResolver.resolve(entityClass);
        return new CacheAsideService<>(
                cacheProvider, repository, metadata.cacheKeyForId(), serializer, metadata.entryTtl());
    }

    public Optional<M> get(ID id) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        var cacheLookupStartedAt = System.nanoTime();
        var cached = cacheProvider.get(key);
        if (cached != null) {
            metrics.onCacheHit(key, elapsedSince(cacheLookupStartedAt));
            return Optional.of(serializer.deserialize(cached));
        }
        metrics.onCacheMiss(key, elapsedSince(cacheLookupStartedAt));

        var originLoadStartedAt = System.nanoTime();
        var loaded = repository.findById(id);
        metrics.onOriginLoad(key, elapsedSince(originLoadStartedAt));

        loaded.ifPresent(entity -> putInCache(key, entity));
        return loaded;
    }

    public void evict(ID id) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        cacheProvider.invalidate(key);
        metrics.onCacheEvict(key);
    }

    /** After updating the backing store, store the fresh value in the cache (optional cache-aside write path). */
    public void putCached(ID id, M value) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        Objects.requireNonNull(value, "value");
        var key = requireKey(cacheKeyForId.apply(id));
        putInCache(key, value);
    }

    private void putInCache(String key, M value) {
        var putStartedAt = System.nanoTime();
        cacheProvider.put(key, serializer.serialize(value), entryTtl);
        metrics.onCachePut(key, elapsedSince(putStartedAt));
    }

    private static String requireKey(String key) {
        return Objects.requireNonNull(key, CACHE_KEY_REQUIRED);
    }
}
