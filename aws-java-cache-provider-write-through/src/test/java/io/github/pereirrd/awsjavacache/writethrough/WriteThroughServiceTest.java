package io.github.pereirrd.awsjavacache.writethrough;

import static io.github.pereirrd.awsjavacache.writethrough.constants.WriteThroughErrorMessages.CACHE_INVALIDATE_AFTER_ORIGIN_DELETE_FAILED;
import static io.github.pereirrd.awsjavacache.writethrough.constants.WriteThroughErrorMessages.CACHE_UPDATE_AFTER_ORIGIN_SAVE_FAILED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pereirrd.awsjavacache.api.exception.CacheException;
import io.github.pereirrd.awsjavacache.api.metrics.CacheMetrics;
import io.github.pereirrd.awsjavacache.api.repository.BackingRepository;
import io.github.pereirrd.awsjavacache.api.serialization.CacheValueSerializer;
import io.github.pereirrd.awsjavacache.core.CacheProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WriteThroughServiceTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private StubCache cache;
    private StubRepository repository;
    private RecordingCacheMetrics metrics;
    private WriteThroughService<Long, String> service;

    @BeforeEach
    void setUp() {
        cache = new StubCache();
        repository = new StubRepository();
        metrics = new RecordingCacheMetrics();
        service = new WriteThroughService<>(
                cache,
                repository,
                id -> "user:" + id,
                entity -> Long.parseLong(entity.split(":")[0]),
                CacheValueSerializer.utf8Strings(),
                TTL,
                metrics);
    }

    @Test
    void get_returnsFromCacheOnHitWithoutQueryingRepository() {
        cache.store.put("user:1", "1:alice");
        assertThat(service.get(1L)).contains("1:alice");
        assertThat(repository.findByIdCount.get()).isZero();
    }

    @Test
    void get_onMiss_loadsFromRepository_putsWithTtlAndReturns() {
        repository.findByIdResult = Optional.of("2:bob");
        assertThat(service.get(2L)).contains("2:bob");
        assertThat(cache.store.get("user:2")).isEqualTo("2:bob");
        assertThat(cache.lastPutTtl).isEqualTo(TTL);
    }

    @Test
    void save_persistsToRepositoryThenUpdatesCacheWithTtl() {
        var saved = service.save("3:carol");
        assertThat(saved).isEqualTo("3:carol");
        assertThat(repository.saveCount.get()).isEqualTo(1);
        assertThat(cache.store.get("user:3")).isEqualTo("3:carol");
        assertThat(cache.lastPutTtl).isEqualTo(TTL);
    }

    @Test
    void save_whenRepositoryFails_doesNotUpdateCache() {
        repository.failSave = true;
        assertThatThrownBy(() -> service.save("4:dana")).isInstanceOf(IllegalStateException.class);
        assertThat(cache.store).isEmpty();
        assertThat(cache.putThreeArgCalls.get()).isZero();
    }

    @Test
    void save_whenCacheUpdateFailsAfterOriginSave_throwsCacheException() {
        cache.failPut = true;
        assertThatThrownBy(() -> service.save("5:erin"))
                .isInstanceOf(CacheException.class)
                .hasMessage(CACHE_UPDATE_AFTER_ORIGIN_SAVE_FAILED);
        assertThat(repository.saveCount.get()).isEqualTo(1);
    }

    @Test
    void deleteById_removesFromRepositoryThenInvalidatesCache() {
        cache.store.put("user:6", "6:frank");
        service.deleteById(6L);
        assertThat(repository.deleteByIdCount.get()).isEqualTo(1);
        assertThat(repository.lastDeletedId).isEqualTo(6L);
        assertThat(cache.invalidated).containsExactly("user:6");
        assertThat(cache.store).doesNotContainKey("user:6");
    }

    @Test
    void deleteById_whenRepositoryFails_doesNotInvalidateCache() {
        cache.store.put("user:7", "7:grace");
        repository.failDelete = true;
        assertThatThrownBy(() -> service.deleteById(7L)).isInstanceOf(IllegalStateException.class);
        assertThat(cache.invalidated).isEmpty();
        assertThat(cache.store).containsKey("user:7");
    }

    @Test
    void deleteById_whenCacheInvalidateFailsAfterOriginDelete_throwsCacheException() {
        cache.failInvalidate = true;
        assertThatThrownBy(() -> service.deleteById(8L))
                .isInstanceOf(CacheException.class)
                .hasMessage(CACHE_INVALIDATE_AFTER_ORIGIN_DELETE_FAILED);
        assertThat(repository.deleteByIdCount.get()).isEqualTo(1);
    }

    @Test
    void save_recordsCachePut() {
        service.save("10:ivan");
        assertThat(metrics.putKeys).containsExactly("user:10");
    }

    @Test
    void deleteById_recordsCacheEvict() {
        service.deleteById(11L);
        assertThat(metrics.evictedKeys).containsExactly("user:11");
    }

    @Test
    void evict_invalidatesResolvedKeyWithoutTouchingRepository() {
        cache.store.put("user:9", "9:henry");
        service.evict(9L);
        assertThat(cache.invalidated).containsExactly("user:9");
        assertThat(repository.deleteByIdCount.get()).isZero();
    }

    private static final class StubCache implements CacheProvider {

        final Map<String, String> store = new HashMap<>();
        final AtomicInteger putThreeArgCalls = new AtomicInteger();
        Duration lastPutTtl;
        List<String> invalidated = new ArrayList<>();
        boolean failPut;
        boolean failInvalidate;

        @Override
        public String get(String key) {
            return store.get(key);
        }

        @Override
        public void put(String key, String value) {
            store.put(key, value);
        }

        @Override
        public void put(String key, String value, Duration ttl) {
            if (failPut) {
                throw new IllegalStateException("cache put failed");
            }
            putThreeArgCalls.incrementAndGet();
            lastPutTtl = ttl;
            store.put(key, value);
        }

        @Override
        public void remove(String key) {
            store.remove(key);
        }

        @Override
        public void clear() {
            store.clear();
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public void flush() {
            store.clear();
        }

        @Override
        public void invalidate(String key) {
            if (failInvalidate) {
                throw new IllegalStateException("cache invalidate failed");
            }
            invalidated.add(key);
            store.remove(key);
        }
    }

    private static final class StubRepository implements BackingRepository<Long, String> {

        final AtomicInteger findByIdCount = new AtomicInteger();
        final AtomicInteger saveCount = new AtomicInteger();
        final AtomicInteger deleteByIdCount = new AtomicInteger();
        Optional<String> findByIdResult = Optional.empty();
        Long lastDeletedId;
        boolean failSave;
        boolean failDelete;

        @Override
        public Optional<String> findById(Long id) {
            findByIdCount.incrementAndGet();
            return findByIdResult;
        }

        @Override
        public String save(String entity) {
            if (failSave) {
                throw new IllegalStateException("repository save failed");
            }
            saveCount.incrementAndGet();
            return entity;
        }

        @Override
        public void deleteById(Long id) {
            if (failDelete) {
                throw new IllegalStateException("repository delete failed");
            }
            deleteByIdCount.incrementAndGet();
            lastDeletedId = id;
        }
    }

    private static final class RecordingCacheMetrics implements CacheMetrics {

        final List<String> putKeys = new ArrayList<>();
        final List<String> evictedKeys = new ArrayList<>();

        @Override
        public void onCachePut(String cacheKey, Duration latency) {
            putKeys.add(cacheKey);
        }

        @Override
        public void onCacheEvict(String cacheKey) {
            evictedKeys.add(cacheKey);
        }
    }
}
