package io.github.pereirrd.awsjavacache.cacheaside;

import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_ID_REQUIRED;
import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_KEY_REQUIRED;

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

    public CacheAsideService(
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

    public Optional<M> get(ID id) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        var cached = cacheProvider.get(key);
        if (cached != null) {
            return Optional.of(serializer.deserialize(cached));
        }
        var loaded = repository.findById(id);
        loaded.ifPresent(entity -> cacheProvider.put(key, serializer.serialize(entity), entryTtl));
        
        return loaded;
    }

    public void evict(ID id) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        cacheProvider.invalidate(key);
    }

    /** After updating the backing store, store the fresh value in the cache (optional cache-aside write path). */
    public void putCached(ID id, M value) {
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        Objects.requireNonNull(value, "value");
        var key = requireKey(cacheKeyForId.apply(id));
        cacheProvider.put(key, serializer.serialize(value), entryTtl);
    }

    private static String requireKey(String key) {
        return Objects.requireNonNull(key, CACHE_KEY_REQUIRED);
    }
}
