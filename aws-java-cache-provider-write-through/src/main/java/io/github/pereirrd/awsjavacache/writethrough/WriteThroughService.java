package io.github.pereirrd.awsjavacache.writethrough;

import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_ID_REQUIRED;
import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_KEY_REQUIRED;
import static io.github.pereirrd.awsjavacache.util.CacheMetricsSupport.elapsedSince;
import static io.github.pereirrd.awsjavacache.writethrough.constants.WriteThroughErrorMessages.CACHE_INVALIDATE_AFTER_ORIGIN_DELETE_FAILED;
import static io.github.pereirrd.awsjavacache.writethrough.constants.WriteThroughErrorMessages.CACHE_UPDATE_AFTER_ORIGIN_SAVE_FAILED;
import static io.github.pereirrd.awsjavacache.writethrough.constants.WriteThroughErrorMessages.ENTITY_REQUIRED;
import static io.github.pereirrd.awsjavacache.writethrough.constants.WriteThroughErrorMessages.ID_FROM_ENTITY_REQUIRED;

import io.github.pereirrd.awsjavacache.api.exception.CacheException;
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
 * Write-through coordination: each {@link #save(Object)} persists to the backing store first, then updates the cache;
 * {@link #deleteById(Object)} removes from the origin first, then invalidates the cache entry. Reads use the cache with
 * origin load on miss (*single-flight* per key).
 *
 * <p><strong>Partial failure policy:</strong> origin operations run before cache side-effects. If the origin succeeds and
 * the cache step fails, a {@link CacheException} is thrown and the cache entry may be stale until {@link #evict(Object)}
 * or a successful {@link #save(Object)}.
 *
 * @param <ID> identifier type
 * @param <M> model type stored in cache and repository
 */
public final class WriteThroughService<ID, M> {

    private final CacheProvider cacheProvider;
    private final BackingRepository<ID, M> repository;
    private final Function<ID, String> cacheKeyForId;
    private final Function<M, ID> idFromEntity;
    private final CacheValueSerializer<M> serializer;
    private final Duration entryTtl;
    private final CacheMetrics metrics;
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    public WriteThroughService(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Function<ID, String> cacheKeyForId,
            Function<M, ID> idFromEntity,
            CacheValueSerializer<M> serializer,
            Duration entryTtl) {
        this(cacheProvider, repository, cacheKeyForId, idFromEntity, serializer, entryTtl, CacheMetrics.NO_OP);
    }

    public WriteThroughService(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Function<ID, String> cacheKeyForId,
            Function<M, ID> idFromEntity,
            CacheValueSerializer<M> serializer,
            Duration entryTtl,
            CacheMetrics metrics) {
        this.cacheProvider = Objects.requireNonNull(cacheProvider, "cacheProvider");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cacheKeyForId = Objects.requireNonNull(cacheKeyForId, "cacheKeyForId");
        this.idFromEntity = Objects.requireNonNull(idFromEntity, "idFromEntity");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.entryTtl = Objects.requireNonNull(entryTtl, "entryTtl");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
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
        return loadAndCache(key, id);
    }

    /**
     * Persists {@code entity} in the backing store, then stores the saved value in the cache under the resolved key.
     */
    public M save(M entity) {
        Objects.requireNonNull(entity, ENTITY_REQUIRED);
        var saved = repository.save(entity);
        updateCacheAfterSave(saved);
        return saved;
    }

    /** Removes {@code id} from the backing store, then invalidates the cache entry. */
    public void deleteById(ID id) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        repository.deleteById(id);
        invalidateCacheAfterDelete(key);
    }

    /** Drops a stale cache entry without touching the backing store. */
    public void evict(ID id) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        cacheProvider.invalidate(key);
        metrics.onCacheEvict(key);
    }

    private void updateCacheAfterSave(M saved) {
        var id = Objects.requireNonNull(idFromEntity.apply(saved), ID_FROM_ENTITY_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        try {
            putInCache(key, saved);
        } catch (RuntimeException e) {
            throw new CacheException(CACHE_UPDATE_AFTER_ORIGIN_SAVE_FAILED, e);
        }
    }

    private void invalidateCacheAfterDelete(String key) {
        try {
            cacheProvider.invalidate(key);
            metrics.onCacheEvict(key);
        } catch (RuntimeException e) {
            throw new CacheException(CACHE_INVALIDATE_AFTER_ORIGIN_DELETE_FAILED, e);
        }
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
