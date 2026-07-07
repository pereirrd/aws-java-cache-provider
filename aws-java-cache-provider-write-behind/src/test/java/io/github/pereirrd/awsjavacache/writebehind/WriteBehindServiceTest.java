package io.github.pereirrd.awsjavacache.writebehind;

import static io.github.pereirrd.awsjavacache.writebehind.constants.WriteBehindErrorMessages.QUEUE_FULL_BACKPRESSURE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pereirrd.awsjavacache.api.exception.CacheException;
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

class WriteBehindServiceTest {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final WriteBehindConfig FAST_FLUSH_CONFIG =
            new WriteBehindConfig(1_000, 50, Duration.ofMillis(50), Duration.ofSeconds(5));

    private StubCache cache;
    private StubRepository repository;
    private RecordingMetrics metrics;
    private WriteBehindService<Long, String> service;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        cache = new StubCache();
        repository = new StubRepository();
        metrics = new RecordingMetrics();
        service = newService(FAST_FLUSH_CONFIG);
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @AfterEach
    void tearDown() {
        service.close();
        executor.shutdownNow();
    }

    @Test
    void save_updatesCacheImmediatelyBeforeOriginPersists() {
        var saved = service.save("3:carol");
        assertThat(saved).isEqualTo("3:carol");
        assertThat(cache.store.get("user:3")).isEqualTo("3:carol");
        assertThat(cache.lastPutTtl).isEqualTo(TTL);
        service.flush();
        assertThat(repository.saveCount.get()).isEqualTo(1);
        assertThat(repository.savedEntities).containsExactly("3:carol");
    }

    @Test
    void deleteById_invalidatesCacheImmediatelyAndDeletesOriginAfterFlush() {
        cache.store.put("user:6", "6:frank");
        service.deleteById(6L);
        assertThat(cache.invalidated).containsExactly("user:6");
        assertThat(cache.store).doesNotContainKey("user:6");
        service.flush();
        assertThat(repository.deleteByIdCount.get()).isEqualTo(1);
        assertThat(repository.lastDeletedId).isEqualTo(6L);
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
    void flush_processesPendingOperationsInBatches() {
        service.save("10:ten");
        service.save("11:eleven");
        service.save("12:twelve");
        service.flush();
        assertThat(repository.saveCount.get()).isEqualTo(3);
        assertThat(service.queueStats().queuedCount()).isZero();
        assertThat(service.queueStats().flushedOperations()).isEqualTo(3);
    }

    @Test
    void close_drainsPendingWritesBeforeStoppingProcessor() {
        service.save("20:twenty");
        service.close();
        assertThat(repository.saveCount.get()).isEqualTo(1);
        assertThatThrownBy(() -> service.save("21:twenty-one")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void backpressure_whenQueueFull_throwsCacheExceptionAfterCacheUpdate() throws Exception {
        repository.saveDelayMs = 300;
        var tightService = newService(new WriteBehindConfig(1, 10, Duration.ofHours(1), Duration.ofSeconds(5)));
        try {
            tightService.save("30:thirty");
            Thread.sleep(50);
            tightService.save("31:thirty-one");
            assertThat(cache.store.get("user:31")).isEqualTo("31:thirty-one");
            assertThatThrownBy(() -> tightService.save("32:thirty-two"))
                    .isInstanceOf(CacheException.class)
                    .hasMessage(QUEUE_FULL_BACKPRESSURE);
            assertThat(tightService.queueStats().rejectedOperations()).isEqualTo(1);
        } finally {
            tightService.close();
        }
    }

    @Test
    void concurrent_saves_allReachRepositoryAfterFlush() throws Exception {
        var threadCount = 24;
        var startGate = new CountDownLatch(1);
        var futures = new ArrayList<Future<String>>();
        for (var i = 0; i < threadCount; i++) {
            var entity = i + ":user";
            futures.add(executor.submit(() -> {
                startGate.await();
                return service.save(entity);
            }));
        }
        startGate.countDown();
        for (var future : futures) {
            assertThat(future.get()).isNotNull();
        }
        service.flush();
        assertThat(repository.saveCount.get()).isEqualTo(threadCount);
    }

    @Test
    void originFailure_recordsFailedOperationsWithoutStoppingQueue() {
        repository.failSave = true;
        service.save("40:fail");
        service.flush();
        assertThat(service.queueStats().failedOperations()).isEqualTo(1);
        repository.failSave = false;
        service.save("41:ok");
        service.flush();
        assertThat(repository.saveCount.get()).isEqualTo(1);
        assertThat(service.queueStats().flushedOperations()).isEqualTo(2);
    }

    @Test
    void metrics_receiveEnqueueFlushAndBackpressureEvents() throws Exception {
        service.save("50:fifty");
        service.flush();
        assertThat(metrics.enqueued).containsExactly(WriteBehindOperationType.SAVE);
        assertThat(metrics.flushedBatches).containsExactly(1);

        repository.saveDelayMs = 300;
        var tightService = newService(new WriteBehindConfig(1, 10, Duration.ofHours(1), Duration.ofSeconds(5)));
        try {
            tightService.save("51:fifty-one");
            Thread.sleep(50);
            tightService.save("52:fifty-two");
            assertThatThrownBy(() -> tightService.save("53:fifty-three")).isInstanceOf(CacheException.class);
            assertThat(metrics.rejected).contains(WriteBehindOperationType.SAVE);
        } finally {
            tightService.close();
        }
    }

    @Test
    void evict_invalidatesResolvedKeyWithoutTouchingRepositoryOrQueue() {
        cache.store.put("user:9", "9:henry");
        service.evict(9L);
        assertThat(cache.invalidated).containsExactly("user:9");
        assertThat(repository.deleteByIdCount.get()).isZero();
        assertThat(repository.saveCount.get()).isZero();
    }

    private WriteBehindService<Long, String> newService(WriteBehindConfig config) {
        return new WriteBehindService<>(
                cache,
                repository,
                id -> "user:" + id,
                entity -> Long.parseLong(entity.split(":")[0]),
                CacheValueSerializer.utf8Strings(),
                TTL,
                config,
                metrics);
    }

    private static final class RecordingMetrics implements WriteBehindMetrics {

        final List<WriteBehindOperationType> enqueued = new ArrayList<>();
        final List<Integer> flushedBatches = new ArrayList<>();
        final List<WriteBehindOperationType> rejected = new ArrayList<>();

        @Override
        public void onEnqueued(WriteBehindOperationType operationType) {
            enqueued.add(operationType);
        }

        @Override
        public void onBatchFlushed(int operationCount) {
            flushedBatches.add(operationCount);
        }

        @Override
        public void onBackpressureRejected(WriteBehindOperationType operationType) {
            rejected.add(operationType);
        }
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
        final AtomicInteger saveCount = new AtomicInteger();
        final AtomicInteger deleteByIdCount = new AtomicInteger();
        final List<String> savedEntities = new ArrayList<>();
        Optional<String> findByIdResult = Optional.empty();
        Long lastDeletedId;
        boolean failSave;
        long saveDelayMs;

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
            if (saveDelayMs > 0) {
                try {
                    Thread.sleep(saveDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted during save", e);
                }
            }
            saveCount.incrementAndGet();
            savedEntities.add(entity);
            return entity;
        }

        @Override
        public void deleteById(Long id) {
            deleteByIdCount.incrementAndGet();
            lastDeletedId = id;
        }
    }
}
