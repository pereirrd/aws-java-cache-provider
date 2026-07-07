package io.github.pereirrd.awsjavacache.writethrough;

import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_ID_REQUIRED;
import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_KEY_REQUIRED;
import static io.github.pereirrd.awsjavacache.writethrough.constants.WriteThroughErrorMessages.CACHE_INVALIDATE_AFTER_ORIGIN_DELETE_FAILED;
import static io.github.pereirrd.awsjavacache.writethrough.constants.WriteThroughErrorMessages.CACHE_UPDATE_AFTER_ORIGIN_SAVE_FAILED;
import static io.github.pereirrd.awsjavacache.writethrough.constants.WriteThroughErrorMessages.ENTITY_REQUIRED;
import static io.github.pereirrd.awsjavacache.writethrough.constants.WriteThroughErrorMessages.ID_FROM_ENTITY_REQUIRED;

import io.github.pereirrd.awsjavacache.api.exception.CacheException;
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
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    public WriteThroughService(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Function<ID, String> cacheKeyForId,
            Function<M, ID> idFromEntity,
            CacheValueSerializer<M> serializer,
            Duration entryTtl) {
        this.cacheProvider = Objects.requireNonNull(cacheProvider, "cacheProvider");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cacheKeyForId = Objects.requireNonNull(cacheKeyForId, "cacheKeyForId");
        this.idFromEntity = Objects.requireNonNull(idFromEntity, "idFromEntity");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.entryTtl = Objects.requireNonNull(entryTtl, "entryTtl");
    }

    public Optional<M> get(ID id) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        var cached = cacheProvider.get(key);
        if (cached != null) {
            return Optional.of(serializer.deserialize(cached));
        }
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
    }

    private void updateCacheAfterSave(M saved) {
        var id = Objects.requireNonNull(idFromEntity.apply(saved), ID_FROM_ENTITY_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        try {
            cacheProvider.put(key, serializer.serialize(saved), entryTtl);
        } catch (RuntimeException e) {
            throw new CacheException(CACHE_UPDATE_AFTER_ORIGIN_SAVE_FAILED, e);
        }
    }

    private void invalidateCacheAfterDelete(String key) {
        try {
            cacheProvider.invalidate(key);
        } catch (RuntimeException e) {
            throw new CacheException(CACHE_INVALIDATE_AFTER_ORIGIN_DELETE_FAILED, e);
        }
    }

    private Optional<M> loadAndCache(String key, ID id) {
        var lock = loadLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            try {
                var cachedAfterLock = cacheProvider.get(key);
                if (cachedAfterLock != null) {
                    return Optional.of(serializer.deserialize(cachedAfterLock));
                }
                var loaded = repository.findById(id);
                loaded.ifPresent(entity -> cacheProvider.put(key, serializer.serialize(entity), entryTtl));
                return loaded;
            } finally {
                loadLocks.remove(key, lock);
            }
        }
    }

    private static String requireKey(String key) {
        return Objects.requireNonNull(key, CACHE_KEY_REQUIRED);
    }
}
