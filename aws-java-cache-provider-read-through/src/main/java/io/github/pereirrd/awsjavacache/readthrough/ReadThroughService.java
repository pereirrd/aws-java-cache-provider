package io.github.pereirrd.awsjavacache.readthrough;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Read-through reads: callers interact only with this service for lookups. On cache miss the loader
 * ({@link BackingRepository#findById(Object)}) runs inside the cache layer, populates the entry, and returns
 * the value. Concurrent misses for the same key coalesce to a single load (*single-flight*).
 *
 * <p>Writes to the origin remain the application's responsibility; call {@link #evict(Object)} after mutations.
 *
 * @param <ID> identifier type
 * @param <M> model type stored in cache and repository
 */
public final class ReadThroughService<ID, M> {

    private final CacheProvider cacheProvider;
    private final BackingRepository<ID, M> repository;
    private final Function<ID, String> cacheKeyForId;
    private final CacheValueSerializer<M> serializer;
    private final Duration entryTtl;
    private final CacheMetrics metrics;
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    public ReadThroughService(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Function<ID, String> cacheKeyForId,
            CacheValueSerializer<M> serializer,
            Duration entryTtl) {
        this(cacheProvider, repository, cacheKeyForId, serializer, entryTtl, CacheMetrics.NO_OP);
    }

    public ReadThroughService(
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
     * Returns the value for {@code id}, loading through the cache on miss.
     */
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
        return loadAndCache(key, id);
    }

    /** Removes the cached entry for {@code id} after the backing store was updated or deleted. */
    public void evict(ID id) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        cacheProvider.invalidate(key);
        metrics.onCacheEvict(key);
    }

    private Optional<M> loadAndCache(String key, ID id) {
        var lock = loadLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            try {
                var cacheLookupStartedAt = System.nanoTime();
                var cachedAfterLock = cacheProvider.get(key);
                if (cachedAfterLock != null) {
                    metrics.onCacheHit(key, elapsedSince(cacheLookupStartedAt));
                    return Optional.of(serializer.deserialize(cachedAfterLock));
                }
                var originLoadStartedAt = System.nanoTime();
                var loaded = repository.findById(id);
                metrics.onOriginLoad(key, elapsedSince(originLoadStartedAt));
                loaded.ifPresent(entity -> putInCache(key, entity));
                return loaded;
            } finally {
                loadLocks.remove(key, lock);
            }
        }
    }

    private void putInCache(String key, M entity) {
        var putStartedAt = System.nanoTime();
        cacheProvider.put(key, serializer.serialize(entity), entryTtl);
        metrics.onCachePut(key, elapsedSince(putStartedAt));
    }

    private static String requireKey(String key) {
        return Objects.requireNonNull(key, CACHE_KEY_REQUIRED);
    }
}
