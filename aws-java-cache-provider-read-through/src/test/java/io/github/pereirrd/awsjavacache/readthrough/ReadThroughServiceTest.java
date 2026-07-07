package io.github.pereirrd.awsjavacache.readthrough;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadThroughServiceTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private StubCache cache;
    private StubRepository repository;
    private RecordingCacheMetrics metrics;
    private ReadThroughService<Long, String> service;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        cache = new StubCache();
        repository = new StubRepository();
        metrics = new RecordingCacheMetrics();
        service = new ReadThroughService<>(
                cache, repository, id -> "user:" + id, CacheValueSerializer.utf8Strings(), TTL, metrics);
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void get_returnsFromCacheOnHitWithoutQueryingRepository() {
        cache.store.put("user:1", "alice");
        assertThat(service.get(1L)).contains("alice");
        assertThat(repository.findByIdCount.get()).isZero();
        assertThat(cache.putThreeArgCalls.get()).isZero();
    }

    @Test
    void get_onMiss_loadsFromRepository_putsWithTtlAndReturns() {
        repository.findByIdResult = Optional.of("bob");
        assertThat(service.get(2L)).contains("bob");
        assertThat(cache.store.get("user:2")).isEqualTo("bob");
        assertThat(cache.lastPutTtl).isEqualTo(TTL);
        assertThat(repository.findByIdCount.get()).isEqualTo(1);
    }

    @Test
    void get_onMiss_whenAbsent_doesNotPopulateCache() {
        repository.findByIdResult = Optional.empty();
        assertThat(service.get(3L)).isEmpty();
        assertThat(cache.store).doesNotContainKey("user:3");
        assertThat(cache.putThreeArgCalls.get()).isZero();
    }

    @Test
    void get_underConcurrentMiss_loadsFromRepositoryOnce() throws Exception {
        repository.findByIdResult = Optional.of("bob");
        repository.loadDelayMs = 200;
        var threadCount = 12;
        var startGate = new CountDownLatch(1);
        var futures = new ArrayList<Future<Optional<String>>>();
        for (var i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startGate.await();
                return service.get(2L);
            }));
        }
        startGate.countDown();
        for (var future : futures) {
            assertThat(future.get()).contains("bob");
        }
        assertThat(repository.findByIdCount.get()).isEqualTo(1);
        assertThat(cache.putThreeArgCalls.get()).isEqualTo(1);
    }

    @Test
    void get_underConcurrentMissForDifferentKeys_loadsEachKeyOnce() throws Exception {
        repository.loadDelayMs = 100;
        repository.resultsById = Map.of(10L, Optional.of("ten"), 20L, Optional.of("twenty"));
        var startGate = new CountDownLatch(1);
        var futures = List.of(
                executor.submit(() -> {
                    startGate.await();
                    return service.get(10L);
                }),
                executor.submit(() -> {
                    startGate.await();
                    return service.get(10L);
                }),
                executor.submit(() -> {
                    startGate.await();
                    return service.get(20L);
                }),
                executor.submit(() -> {
                    startGate.await();
                    return service.get(20L);
                }));
        startGate.countDown();
        assertThat(futures.get(0).get()).contains("ten");
        assertThat(futures.get(1).get()).contains("ten");
        assertThat(futures.get(2).get()).contains("twenty");
        assertThat(futures.get(3).get()).contains("twenty");
        assertThat(repository.findByIdCount.get()).isEqualTo(2);
    }

    @Test
    void get_underConcurrentMiss_recordsSingleOriginLoad() throws Exception {
        repository.findByIdResult = Optional.of("bob");
        repository.loadDelayMs = 200;
        var threadCount = 12;
        var startGate = new CountDownLatch(1);
        var futures = new ArrayList<Future<Optional<String>>>();
        for (var i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startGate.await();
                return service.get(2L);
            }));
        }
        startGate.countDown();
        for (var future : futures) {
            assertThat(future.get()).contains("bob");
        }
        assertThat(metrics.originLoadKeys).containsExactly("user:2");
        assertThat(metrics.putKeys).containsExactly("user:2");
    }

    @Test
    void evict_invalidatesResolvedKey() {
        cache.store.put("user:4", "x");
        service.evict(4L);
        assertThat(cache.invalidated).containsExactly("user:4");
    }

    private static final class StubCache implements CacheProvider {

        final Map<String, String> store = new HashMap<>();
        final AtomicInteger putThreeArgCalls = new AtomicInteger();
        Duration lastPutTtl;
        List<String> invalidated = new ArrayList<>();

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
        Map<Long, Optional<String>> resultsById = Map.of();
        long loadDelayMs;

        @Override
        public Optional<String> findById(Long id) {
            findByIdCount.incrementAndGet();
            if (!resultsById.isEmpty()) {
                return resultsById.getOrDefault(id, Optional.empty());
            }
            if (loadDelayMs > 0) {
                try {
                    Thread.sleep(loadDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted during load", e);
                }
            }
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

        final List<String> originLoadKeys = new ArrayList<>();
        final List<String> putKeys = new ArrayList<>();

        @Override
        public void onOriginLoad(String cacheKey, Duration latency) {
            originLoadKeys.add(cacheKey);
        }

        @Override
        public void onCachePut(String cacheKey, Duration latency) {
            putKeys.add(cacheKey);
        }
    }
}
