package io.github.pereirrd.awsjavacache.writethrough;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.pereirrd.awsjavacache.api.repository.BackingRepository;
import io.github.pereirrd.awsjavacache.api.serialization.CacheValueSerializer;
import io.github.pereirrd.awsjavacache.config.RedisCacheEnvConfig;
import io.github.pereirrd.awsjavacache.core.impl.RedisCacheProvider;
import io.github.pereirrd.awsjavacache.factory.RedisCacheClientFactory;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WriteThroughServiceComposeIT {

    private static final Duration TTL = Duration.ofMinutes(5);

    private RedisCacheProvider cacheProvider;
    private InMemoryRepository repository;
    private WriteThroughService<Long, String> service;

    @BeforeEach
    void setUp() {
        assumeTrue(isRedisAvailable(), "Redis not reachable — run: docker compose up -d");
        var redisClient = RedisCacheClientFactory.from(RedisCacheEnvConfig.fromEnvironment());
        cacheProvider = RedisCacheProvider.utf8Strings(redisClient);
        cacheProvider.flush();
        repository = new InMemoryRepository();
        service = new WriteThroughService<>(
                cacheProvider,
                repository,
                id -> "writethrough:user:" + id,
                entity -> Long.parseLong(entity.split(":")[0]),
                CacheValueSerializer.utf8Strings(),
                TTL);
    }

    @AfterEach
    void tearDown() {
        if (cacheProvider != null) {
            cacheProvider.close();
        }
    }

    @Test
    void save_persistsToRepositoryThenUpdatesRedis() {
        var saved = service.save("1:alice");
        assertThat(saved).isEqualTo("1:alice");
        assertThat(repository.saveCount.get()).isEqualTo(1);
        assertThat(cacheProvider.get("writethrough:user:1")).isEqualTo("1:alice");
    }

    @Test
    void get_onHit_readsFromRedisWithoutRepositoryLookup() {
        service.save("2:bob");
        repository.findByIdCount.set(0);
        assertThat(service.get(2L)).contains("2:bob");
        assertThat(repository.findByIdCount.get()).isZero();
    }

    @Test
    void deleteById_removesFromRepositoryAndInvalidatesRedis() {
        service.save("3:carol");
        service.deleteById(3L);
        assertThat(repository.deleteByIdCount.get()).isEqualTo(1);
        assertThat(cacheProvider.get("writethrough:user:3")).isNull();
    }

    private static boolean isRedisAvailable() {
        for (var attempt = 0; attempt < 5; attempt++) {
            if (pingRedis()) {
                return true;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean pingRedis() {
        try {
            var client = RedisCacheClientFactory.from(RedisCacheEnvConfig.fromEnvironment());
            try (client) {
                var connection = client.connect();
                try (connection) {
                    return "PONG".equals(connection.sync().ping());
                }
            }
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static final class InMemoryRepository implements BackingRepository<Long, String> {

        final Map<Long, String> store = new HashMap<>();
        final AtomicInteger findByIdCount = new AtomicInteger();
        final AtomicInteger saveCount = new AtomicInteger();
        final AtomicInteger deleteByIdCount = new AtomicInteger();

        @Override
        public Optional<String> findById(Long id) {
            findByIdCount.incrementAndGet();
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public String save(String entity) {
            saveCount.incrementAndGet();
            var id = Long.parseLong(entity.split(":")[0]);
            store.put(id, entity);
            return entity;
        }

        @Override
        public void deleteById(Long id) {
            deleteByIdCount.incrementAndGet();
            store.remove(id);
        }
    }
}
