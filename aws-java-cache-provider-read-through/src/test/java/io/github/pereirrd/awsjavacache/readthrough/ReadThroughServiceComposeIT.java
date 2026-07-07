package io.github.pereirrd.awsjavacache.readthrough;

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

class ReadThroughServiceComposeIT {

    private static final Duration TTL = Duration.ofMinutes(5);

    private RedisCacheProvider cacheProvider;
    private InMemoryRepository repository;
    private ReadThroughService<Long, String> service;

    @BeforeEach
    void setUp() {
        assumeTrue(isRedisAvailable(), "Redis not reachable — run: docker compose up -d");
        var redisClient = RedisCacheClientFactory.from(RedisCacheEnvConfig.fromEnvironment());
        cacheProvider = RedisCacheProvider.utf8Strings(redisClient);
        cacheProvider.flush();
        repository = new InMemoryRepository();
        service = new ReadThroughService<>(
                cacheProvider, repository, id -> "readthrough:user:" + id, CacheValueSerializer.utf8Strings(), TTL);
    }

    @AfterEach
    void tearDown() {
        if (cacheProvider != null) {
            cacheProvider.close();
        }
    }

    @Test
    void get_onMiss_loadsFromRepositoryAndPopulatesRedis() {
        repository.store.put(1L, "alice");
        assertThat(service.get(1L)).contains("alice");
        assertThat(cacheProvider.get("readthrough:user:1")).isEqualTo("alice");
        assertThat(repository.findByIdCount.get()).isEqualTo(1);
    }

    @Test
    void get_onHit_doesNotQueryRepository() {
        repository.store.put(2L, "bob");
        assertThat(service.get(2L)).contains("bob");
        assertThat(service.get(2L)).contains("bob");
        assertThat(repository.findByIdCount.get()).isEqualTo(1);
    }

    @Test
    void evict_removesCachedEntry() {
        repository.store.put(3L, "carol");
        service.get(3L);
        service.evict(3L);
        assertThat(cacheProvider.get("readthrough:user:3")).isNull();
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

        @Override
        public Optional<String> findById(Long id) {
            findByIdCount.incrementAndGet();
            return Optional.ofNullable(store.get(id));
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
}
