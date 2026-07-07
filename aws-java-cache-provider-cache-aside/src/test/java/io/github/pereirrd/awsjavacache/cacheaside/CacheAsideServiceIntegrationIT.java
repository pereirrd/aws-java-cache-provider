package io.github.pereirrd.awsjavacache.cacheaside;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class CacheAsideServiceIntegrationIT {

    private static final String REDIS_IMAGE = "redis:7.4-alpine";
    private static final Duration TTL = Duration.ofMinutes(5);

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse(REDIS_IMAGE)).withExposedPorts(6379);

    private RedisCacheProvider cacheProvider;
    private InMemoryRepository repository;
    private CacheAsideService<Long, String> service;

    @BeforeEach
    void setUp() {
        var redisClient = RedisCacheClientFactory.from(redisConfig());
        cacheProvider = RedisCacheProvider.utf8Strings(redisClient);
        repository = new InMemoryRepository();
        service = new CacheAsideService<>(
                cacheProvider, repository, id -> "user:" + id, CacheValueSerializer.utf8Strings(), TTL);
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
        assertThat(cacheProvider.get("user:1")).isEqualTo("alice");
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
        service.putCached(3L, "carol");
        service.evict(3L);
        assertThat(cacheProvider.get("user:3")).isNull();
    }

    private RedisCacheEnvConfig redisConfig() {
        var env = new HashMap<String, String>();
        env.put(RedisCacheEnvConfig.HOST, REDIS.getHost());
        env.put(RedisCacheEnvConfig.PORT, String.valueOf(REDIS.getMappedPort(6379)));
        env.put(RedisCacheEnvConfig.TLS, "false");
        env.put(RedisCacheEnvConfig.DATABASE, "0");
        return RedisCacheEnvConfig.from(Map.copyOf(env));
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
