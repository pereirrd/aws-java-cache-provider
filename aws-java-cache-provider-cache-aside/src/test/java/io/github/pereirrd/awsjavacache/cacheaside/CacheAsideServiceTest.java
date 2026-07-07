package io.github.pereirrd.awsjavacache.cacheaside;

import static org.assertj.core.api.Assertions.assertThat;

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

class CacheAsideServiceTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private StubCache cache;
    private StubRepository repository;
    private RecordingCacheMetrics metrics;
    private CacheAsideService<Long, String> service;

    @BeforeEach
    void setUp() {
        cache = new StubCache();
        repository = new StubRepository();
        metrics = new RecordingCacheMetrics();
        service = new CacheAsideService<>(
                cache, repository, id -> "user:" + id, CacheValueSerializer.utf8Strings(), TTL, metrics);
    }

    @Test
    void get_returnsFromCacheOnHitWithoutQueryingRepository() {
        cache.store.put("user:1", "alice");
        assertThat(service.get(1L)).contains("alice");
        assertThat(repository.findByIdCount.get()).isZero();
        assertThat(cache.putTwoArgCalls.get()).isZero();
        assertThat(cache.putThreeArgCalls.get()).isZero();
    }

    @Test
    void get_onMiss_loadsFromRepository_putsWithTtlAndReturns() {
        repository.findByIdResult = Optional.of("bob");
        assertThat(service.get(2L)).contains("bob");
        assertThat(cache.store.get("user:2")).isEqualTo("bob");
        assertThat(cache.lastPutTtl).isEqualTo(TTL);
    }

    @Test
    void get_onMiss_whenAbsent_doesNotPopulateCache() {
        repository.findByIdResult = Optional.empty();
        assertThat(service.get(3L)).isEmpty();
        assertThat(cache.store).doesNotContainKey("user:3");
        assertThat(cache.putTwoArgCalls.get()).isZero();
        assertThat(cache.putThreeArgCalls.get()).isZero();
    }

    @Test
    void evict_invalidatesResolvedKey() {
        cache.store.put("user:4", "x");
        service.evict(4L);
        assertThat(cache.invalidated).containsExactly("user:4");
    }

    @Test
    void putCached_serializesAndStoresWithTtl() {
        service.putCached(5L, "pat");
        assertThat(cache.store.get("user:5")).isEqualTo("pat");
        assertThat(cache.lastPutTtl).isEqualTo(TTL);
        assertThat(metrics.putKeys).containsExactly("user:5");
    }

    @Test
    void get_onHit_recordsCacheHitWithoutOriginLoad() {
        cache.store.put("user:6", "cached");
        assertThat(service.get(6L)).contains("cached");
        assertThat(metrics.hitKeys).containsExactly("user:6");
        assertThat(metrics.missKeys).isEmpty();
        assertThat(metrics.originLoadKeys).isEmpty();
    }

    @Test
    void get_onMiss_recordsMissOriginLoadAndPut() {
        repository.findByIdResult = Optional.of("loaded");
        assertThat(service.get(7L)).contains("loaded");
        assertThat(metrics.missKeys).containsExactly("user:7");
        assertThat(metrics.originLoadKeys).containsExactly("user:7");
        assertThat(metrics.putKeys).containsExactly("user:7");
        assertThat(metrics.hitKeys).isEmpty();
    }

    @Test
    void evict_recordsCacheEvict() {
        service.evict(8L);
        assertThat(metrics.evictedKeys).containsExactly("user:8");
    }

    private static final class StubCache implements CacheProvider {

        final Map<String, String> store = new HashMap<>();
        final AtomicInteger putTwoArgCalls = new AtomicInteger();
        final AtomicInteger putThreeArgCalls = new AtomicInteger();
        Duration lastPutTtl;
        List<String> invalidated = new ArrayList<>();

        @Override
        public String get(String key) {
            return store.get(key);
        }

        @Override
        public void put(String key, String value) {
            putTwoArgCalls.incrementAndGet();
            store.put(key, value);
        }

        @Override
        public void put(String key, String value, Duration ttl) {
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
            invalidated.add(key);
            store.remove(key);
        }
    }

    private static final class StubRepository implements BackingRepository<Long, String> {

        final AtomicInteger findByIdCount = new AtomicInteger();
        Optional<String> findByIdResult = Optional.empty();

        @Override
        public Optional<String> findById(Long id) {
            findByIdCount.incrementAndGet();
            return findByIdResult;
        }

        @Override
        public String save(String entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(Long id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingCacheMetrics implements CacheMetrics {

        final List<String> hitKeys = new ArrayList<>();
        final List<String> missKeys = new ArrayList<>();
        final List<String> originLoadKeys = new ArrayList<>();
        final List<String> putKeys = new ArrayList<>();
        final List<String> evictedKeys = new ArrayList<>();

        @Override
        public void onCacheHit(String cacheKey, Duration latency) {
            hitKeys.add(cacheKey);
        }

        @Override
        public void onCacheMiss(String cacheKey, Duration latency) {
            missKeys.add(cacheKey);
        }

        @Override
        public void onOriginLoad(String cacheKey, Duration latency) {
            originLoadKeys.add(cacheKey);
        }

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
