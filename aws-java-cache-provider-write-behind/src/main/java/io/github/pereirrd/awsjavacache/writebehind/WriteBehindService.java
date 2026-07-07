package io.github.pereirrd.awsjavacache.writebehind;

import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_ID_REQUIRED;
import static io.github.pereirrd.awsjavacache.constants.CacheErrorMessages.CACHE_KEY_REQUIRED;
import static io.github.pereirrd.awsjavacache.util.CacheMetricsSupport.elapsedSince;
import static io.github.pereirrd.awsjavacache.writebehind.constants.WriteBehindErrorMessages.CACHE_INVALIDATE_FAILED;
import static io.github.pereirrd.awsjavacache.writebehind.constants.WriteBehindErrorMessages.CACHE_UPDATE_FAILED;
import static io.github.pereirrd.awsjavacache.writebehind.constants.WriteBehindErrorMessages.ENTITY_REQUIRED;
import static io.github.pereirrd.awsjavacache.writebehind.constants.WriteBehindErrorMessages.FLUSH_INTERRUPTED;
import static io.github.pereirrd.awsjavacache.writebehind.constants.WriteBehindErrorMessages.ID_FROM_ENTITY_REQUIRED;
import static io.github.pereirrd.awsjavacache.writebehind.constants.WriteBehindErrorMessages.QUEUE_FULL_BACKPRESSURE;
import static io.github.pereirrd.awsjavacache.writebehind.constants.WriteBehindErrorMessages.SERVICE_CLOSED;
import static io.github.pereirrd.awsjavacache.writebehind.constants.WriteBehindErrorMessages.SHUTDOWN_FLUSH_FAILED;

import io.github.pereirrd.awsjavacache.api.exception.CacheException;
import io.github.pereirrd.awsjavacache.api.metrics.CacheMetrics;
import io.github.pereirrd.awsjavacache.api.repository.BackingRepository;
import io.github.pereirrd.awsjavacache.api.serialization.CacheValueSerializer;
import io.github.pereirrd.awsjavacache.core.CacheProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Write-behind coordination: {@link #save(Object)} and {@link #deleteById(Object)} update the cache first and enqueue
 * asynchronous persistence to the backing store. Reads use the cache with origin load on miss (*single-flight* per
 * key).
 *
 * <p><strong>Async origin:</strong> after {@link #save} / {@link #deleteById} return, the cache reflects the new state
 * but the backing store is updated later on a background thread. The cache may be <strong>ahead</strong> of the origin
 * until the queue drains. Transaction boundaries and idempotent {@link BackingRepository} implementations are the
 * consumer's responsibility — see {@code docs/contrato-transacional.md}.
 *
 * <p><strong>Idempotency:</strong> {@link BackingRepository#save(Object)} and {@link BackingRepository#deleteById(Object)}
 * must tolerate duplicate drains (safe upsert; delete is no-op when already absent).
 *
 * <p><strong>Durability:</strong> the pending-write queue is in-memory only. Call {@link #flush()} or {@link #close()}
 * before shutdown to drain pending operations.
 *
 * <p><strong>Backpressure:</strong> when the queue is full, {@link #save(Object)} and {@link #deleteById(Object)} throw
 * {@link CacheException} after updating the cache (the cache may be ahead of the origin until space is available and
 * the caller retries or {@link #flush()} runs).
 */
public final class WriteBehindService<ID, M> implements AutoCloseable {

    private final CacheProvider cacheProvider;
    private final BackingRepository<ID, M> repository;
    private final Function<ID, String> cacheKeyForId;
    private final Function<M, ID> idFromEntity;
    private final CacheValueSerializer<M> serializer;
    private final Duration entryTtl;
    private final WriteBehindConfig config;
    private final WriteBehindMetrics metrics;
    private final CacheMetrics cacheMetrics;
    private final BlockingQueue<WriteBehindTask<ID, M>> queue;
    private final ReentrantLock processLock = new ReentrantLock();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicLong flushedOperations = new AtomicLong();
    private final AtomicLong failedOperations = new AtomicLong();
    private final AtomicLong rejectedOperations = new AtomicLong();
    private final ConcurrentHashMap<String, Object> loadLocks = new ConcurrentHashMap<>();
    private volatile boolean processorBusy;
    private final Thread processorThread;

    public WriteBehindService(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Function<ID, String> cacheKeyForId,
            Function<M, ID> idFromEntity,
            CacheValueSerializer<M> serializer,
            Duration entryTtl) {
        this(
                cacheProvider,
                repository,
                cacheKeyForId,
                idFromEntity,
                serializer,
                entryTtl,
                WriteBehindConfig.defaults(),
                WriteBehindMetrics.NO_OP);
    }

    public WriteBehindService(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Function<ID, String> cacheKeyForId,
            Function<M, ID> idFromEntity,
            CacheValueSerializer<M> serializer,
            Duration entryTtl,
            WriteBehindConfig config,
            WriteBehindMetrics metrics) {
        this(
                cacheProvider,
                repository,
                cacheKeyForId,
                idFromEntity,
                serializer,
                entryTtl,
                config,
                metrics,
                CacheMetrics.NO_OP);
    }

    public WriteBehindService(
            CacheProvider cacheProvider,
            BackingRepository<ID, M> repository,
            Function<ID, String> cacheKeyForId,
            Function<M, ID> idFromEntity,
            CacheValueSerializer<M> serializer,
            Duration entryTtl,
            WriteBehindConfig config,
            WriteBehindMetrics metrics,
            CacheMetrics cacheMetrics) {
        this.cacheProvider = Objects.requireNonNull(cacheProvider, "cacheProvider");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.cacheKeyForId = Objects.requireNonNull(cacheKeyForId, "cacheKeyForId");
        this.idFromEntity = Objects.requireNonNull(idFromEntity, "idFromEntity");
        this.serializer = Objects.requireNonNull(serializer, "serializer");
        this.entryTtl = Objects.requireNonNull(entryTtl, "entryTtl");
        this.config = Objects.requireNonNull(config, "config");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.cacheMetrics = Objects.requireNonNull(cacheMetrics, "cacheMetrics");
        this.queue = new LinkedBlockingQueue<>(config.queueCapacity());
        this.processorThread =
                Thread.ofPlatform().daemon().name("write-behind-processor").start(this::runProcessorLoop);
    }

    public Optional<M> get(ID id) {
        ensureOpen();
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        var cacheLookupStartedAt = System.nanoTime();
        var cached = cacheProvider.get(key);
        if (cached != null) {
            cacheMetrics.onCacheHit(key, elapsedSince(cacheLookupStartedAt));
            return Optional.of(serializer.deserialize(cached));
        }
        cacheMetrics.onCacheMiss(key, elapsedSince(cacheLookupStartedAt));
        return loadAndCache(key, id);
    }

    /**
     * Updates the cache immediately and enqueues an asynchronous {@link BackingRepository#save(Object)}.
     *
     * @return the same {@code entity} instance after the cache update (origin persistence is asynchronous)
     */
    public M save(M entity) {
        ensureOpen();
        Objects.requireNonNull(entity, ENTITY_REQUIRED);
        var id = Objects.requireNonNull(idFromEntity.apply(entity), ID_FROM_ENTITY_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        updateCache(key, entity);
        enqueue(new WriteBehindTask.SaveTask<>(id, entity), WriteBehindOperationType.SAVE);
        return entity;
    }

    /** Invalidates the cache entry immediately and enqueues an asynchronous {@link BackingRepository#deleteById(Object)}. */
    public void deleteById(ID id) {
        ensureOpen();
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        invalidateCache(key);
        enqueue(new WriteBehindTask.DeleteTask<>(id), WriteBehindOperationType.DELETE);
    }

    /** Drops a stale cache entry without touching the backing store or the pending queue. */
    public void evict(ID id) {
        ensureOpen();
        Objects.requireNonNull(id, CACHE_ID_REQUIRED);
        var key = requireKey(cacheKeyForId.apply(id));
        cacheProvider.invalidate(key);
        cacheMetrics.onCacheEvict(key);
    }

    /** Drains all pending origin writes synchronously on the calling thread. */
    public void flush() {
        ensureOpen();
        drainAndProcessRemaining();
    }

    public WriteBehindQueueStats queueStats() {
        return new WriteBehindQueueStats(
                queue.size(), flushedOperations.get(), failedOperations.get(), rejectedOperations.get());
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            drainAndProcessRemaining();
        } catch (RuntimeException e) {
            throw new CacheException(SHUTDOWN_FLUSH_FAILED, e);
        }
        processorThread.interrupt();
        try {
            processorThread.join(config.shutdownTimeout().toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CacheException(FLUSH_INTERRUPTED, e);
        }
    }

    private void runProcessorLoop() {
        while (!closed.get() || !queue.isEmpty()) {
            WriteBehindTask<ID, M> first;
            try {
                first = queue.poll(config.flushInterval().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                if (closed.get()) {
                    Thread.currentThread().interrupt();
                    return;
                }
                continue;
            }
            if (first == null) {
                continue;
            }
            processorBusy = true;
            processLock.lock();
            try {
                var batch = new ArrayList<WriteBehindTask<ID, M>>(config.batchSize());
                batch.add(first);
                queue.drainTo(batch, config.batchSize() - 1);
                processBatch(batch);
            } finally {
                processorBusy = false;
                processLock.unlock();
            }
        }
    }

    private void drainAndProcessRemaining() {
        processLock.lock();
        try {
            var pending = new ArrayList<WriteBehindTask<ID, M>>();
            queue.drainTo(pending);
            for (var offset = 0; offset < pending.size(); offset += config.batchSize()) {
                var end = Math.min(offset + config.batchSize(), pending.size());
                processBatch(pending.subList(offset, end));
            }
        } finally {
            processLock.unlock();
        }
        waitForProcessorIdle();
    }

    private void waitForProcessorIdle() {
        while (processorBusy) {
            Thread.onSpinWait();
        }
    }

    private void processBatch(List<WriteBehindTask<ID, M>> batch) {
        if (batch.isEmpty()) {
            return;
        }
        batch.forEach(this::applyToOrigin);
        flushedOperations.addAndGet(batch.size());
        metrics.onBatchFlushed(batch.size());
    }

    private void applyToOrigin(WriteBehindTask<ID, M> task) {
        try {
            switch (task) {
                case WriteBehindTask.SaveTask<ID, M> save -> repository.save(save.entity());
                case WriteBehindTask.DeleteTask<ID, M> delete -> repository.deleteById(delete.id());
            }
        } catch (RuntimeException e) {
            failedOperations.incrementAndGet();
            metrics.onOriginFailure(task.operationType(), e);
        }
    }

    private void enqueue(WriteBehindTask<ID, M> task, WriteBehindOperationType operationType) {
        if (!queue.offer(task)) {
            rejectedOperations.incrementAndGet();
            metrics.onBackpressureRejected(operationType);
            throw new CacheException(QUEUE_FULL_BACKPRESSURE);
        }
        metrics.onEnqueued(operationType);
    }

    private void updateCache(String key, M entity) {
        try {
            var putStartedAt = System.nanoTime();
            cacheProvider.put(key, serializer.serialize(entity), entryTtl);
            cacheMetrics.onCachePut(key, elapsedSince(putStartedAt));
        } catch (RuntimeException e) {
            throw new CacheException(CACHE_UPDATE_FAILED, e);
        }
    }

    private void invalidateCache(String key) {
        try {
            cacheProvider.invalidate(key);
            cacheMetrics.onCacheEvict(key);
        } catch (RuntimeException e) {
            throw new CacheException(CACHE_INVALIDATE_FAILED, e);
        }
    }

    private Optional<M> loadAndCache(String key, ID id) {
        var lock = loadLocks.computeIfAbsent(key, ignored -> new Object());
        synchronized (lock) {
            try {
                var cacheLookupStartedAt = System.nanoTime();
                var cachedAfterLock = cacheProvider.get(key);
                if (cachedAfterLock != null) {
                    cacheMetrics.onCacheHit(key, elapsedSince(cacheLookupStartedAt));
                    return Optional.of(serializer.deserialize(cachedAfterLock));
                }
                var originLoadStartedAt = System.nanoTime();
                var loaded = repository.findById(id);
                cacheMetrics.onOriginLoad(key, elapsedSince(originLoadStartedAt));
                loaded.ifPresent(entity -> {
                    var putStartedAt = System.nanoTime();
                    cacheProvider.put(key, serializer.serialize(entity), entryTtl);
                    cacheMetrics.onCachePut(key, elapsedSince(putStartedAt));
                });
                return loaded;
            } finally {
                loadLocks.remove(key, lock);
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException(SERVICE_CLOSED);
        }
    }

    private static String requireKey(String key) {
        return Objects.requireNonNull(key, CACHE_KEY_REQUIRED);
    }
}
