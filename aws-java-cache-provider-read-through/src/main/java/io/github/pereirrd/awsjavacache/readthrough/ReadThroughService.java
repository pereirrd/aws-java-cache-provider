package io.github.pereirrd.awsjavacache.readthrough;

import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_ID_REQUIRED;
import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_KEY_REQUIRED;

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
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();

    public ReadThroughService(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Function<ID, String> cacheKeyForId,
            CacheValueSerializer<M> serializer,
            Duration entryTtl) {
        this.cacheProvider = Objects.requireNonNull(cacheProvider, "cacheProvider");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cacheKeyForId = Objects.requireNonNull(cacheKeyForId, "cacheKeyForId");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.entryTtl = Objects.requireNonNull(entryTtl, "entryTtl");
    }

    /**
     * Returns the value for {@code id}, loading through the cache on miss.
     */
    public Optional<M> get(ID id) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        var cached = cacheProvider.get(key);
        if (cached != null) {
            return Optional.of(serializer.deserialize(cached));
        }
        return loadAndCache(key, id);
    }

    /** Removes the cached entry for {@code id} after the backing store was updated or deleted. */
    public void evict(ID id) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        cacheProvider.invalidate(key);
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
