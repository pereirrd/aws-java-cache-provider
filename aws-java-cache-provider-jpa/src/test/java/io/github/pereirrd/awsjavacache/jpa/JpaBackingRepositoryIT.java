package io.github.pereirrd.awsjavacache.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pereirrd.awsjavacache.api.serialization.CacheValueSerializer;
import io.github.pereirrd.awsjavacache.cacheaside.CacheAsideService;
import io.github.pereirrd.awsjavacache.core.CacheProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JpaBackingRepositoryIT {

    private static final Duration TTL = Duration.ofMinutes(5);

    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;
    private JpaBackingRepository<Long, UserEntity> repository;
    private InMemoryCache cache;
    private CacheAsideService<Long, UserEntity> cacheAside;

    @BeforeEach
    void setUp() {
        var properties = new HashMap<String, Object>();
        properties.put("jakarta.persistence.jdbc.driver", "org.h2.Driver");
        properties.put("jakarta.persistence.jdbc.url", "jdbc:h2:mem:jpa-cache-it;DB_CLOSE_DELAY=-1");
        properties.put("jakarta.persistence.jdbc.user", "sa");
        properties.put("jakarta.persistence.jdbc.password", "");
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.show_sql", "false");

        entityManagerFactory = Persistence.createEntityManagerFactory("jpa-cache-it", properties);
        entityManager = entityManagerFactory.createEntityManager();
        entityManager.getTransaction().begin();

        repository = new JpaBackingRepository<>(entityManager, UserEntity.class);
        cache = new InMemoryCache();
        cacheAside = new CacheAsideService<>(cache, repository, id -> "user:" + id, UserEntitySerializer.INSTANCE, TTL);
    }

    @AfterEach
    void tearDown() {
        if (entityManager != null) {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @Test
    void cacheAside_onMiss_loadsPersistedEntityFromH2() {
        var persisted = repository.save(new UserEntity("alice"));
        entityManager.getTransaction().commit();
        entityManager.getTransaction().begin();

        assertThat(cacheAside.get(persisted.getId())).contains(persisted);
        assertThat(cache.store).containsKey("user:" + persisted.getId());
    }

    @Test
    void cacheAside_onHit_doesNotQueryDatabaseAgain() {
        var persisted = repository.save(new UserEntity("bob"));
        entityManager.getTransaction().commit();
        entityManager.getTransaction().begin();

        assertThat(cacheAside.get(persisted.getId())).contains(persisted);

        entityManager.clear();
        entityManager.getTransaction().commit();
        entityManager.close();
        entityManager = entityManagerFactory.createEntityManager();
        entityManager.getTransaction().begin();
        repository = new JpaBackingRepository<>(entityManager, UserEntity.class);
        cacheAside = new CacheAsideService<>(cache, repository, id -> "user:" + id, UserEntitySerializer.INSTANCE, TTL);

        assertThat(cacheAside.get(persisted.getId())).contains(persisted);
    }

    @Test
    void repository_saveAndFindById_roundTripWithH2() {
        var saved = repository.save(new UserEntity("carol"));
        entityManager.getTransaction().commit();
        entityManager.getTransaction().begin();

        assertThat(repository.findById(saved.getId())).contains(saved);
    }

    @Test
    void repository_deleteById_removesFromH2() {
        var saved = repository.save(new UserEntity("dave"));
        var id = saved.getId();
        repository.deleteById(id);
        entityManager.getTransaction().commit();
        entityManager.getTransaction().begin();

        assertThat(repository.findById(id)).isEmpty();
    }

    private static final class InMemoryCache implements CacheProvider {

        final Map<String, String> store = new HashMap<>();

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
            store.remove(key);
        }
    }

    private enum UserEntitySerializer implements CacheValueSerializer<UserEntity> {
        INSTANCE;

        @Override
        public String serialize(UserEntity value) {
            return value.getId() + ":" + value.getName();
        }

        @Override
        public UserEntity deserialize(String payload) {
            var separator = payload.indexOf(':');
            var id = Long.parseLong(payload.substring(0, separator));
            var name = payload.substring(separator + 1);
            return new UserEntity(id, name);
        }
    }
}
