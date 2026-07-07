package io.github.pereirrd.awsjavacache.cacheaside;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.pereirrd.awsjavacache.api.repository.BackingRepository;
import io.github.pereirrd.awsjavacache.api.serialization.CacheValueSerializer;
import io.github.pereirrd.awsjavacache.cacheaside.annotation.CacheId;
import io.github.pereirrd.awsjavacache.cacheaside.annotation.CacheKey;
import io.github.pereirrd.awsjavacache.cacheaside.annotation.CacheName;
import io.github.pereirrd.awsjavacache.cacheaside.annotation.CacheRegion;
import io.github.pereirrd.awsjavacache.cacheaside.annotation.CacheTtl;
import io.github.pereirrd.awsjavacache.core.CacheProvider;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CacheAsideAnnotationResolverTest {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    @Test
    void resolve_buildsKeyPrefixTtlAndIdExtractorFromRegionAnnotation() {
        var metadata = typedMetadata(RegionAnnotatedUser.class, Long.class);

        assertThat(metadata.cacheKeyPrefix()).isEqualTo("users:");
        assertThat(metadata.cacheKeyForId().apply(42L)).isEqualTo("users:42");
        assertThat(metadata.entryTtl()).isEqualTo(TEN_MINUTES);
        assertThat(metadata.idExtractor().apply(new RegionAnnotatedUser(7L, "ana")))
                .isEqualTo(7L);
    }

    @Test
    void resolve_usesExplicitKeyTemplateOnClass() {
        var metadata = typedMetadata(TemplateAnnotatedUser.class, String.class);

        assertThat(metadata.cacheKeyPrefix()).isEqualTo("profile:");
        assertThat(metadata.cacheKeyForId().apply("abc")).isEqualTo("profile:abc");
        assertThat(metadata.entryTtl()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void resolve_acceptsCacheNameAsRegionAlias() {
        var metadata = typedMetadata(NamedAnnotatedUser.class, Long.class);

        assertThat(metadata.cacheKeyForId().apply(1L)).isEqualTo("orders:1");
    }

    @Test
    void resolve_prefersFieldTtlOverClassTtl() {
        var metadata = CacheAsideAnnotationResolver.resolve(FieldTtlUser.class);

        assertThat(metadata.entryTtl()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void resolve_readsIdFromAnnotatedGetter() {
        var metadata = typedMetadata(GetterIdUser.class, String.class);

        assertThat(metadata.idExtractor().apply(new GetterIdUser("key-9"))).isEqualTo("key-9");
        assertThat(metadata.cacheKeyForId().apply("key-9")).isEqualTo("tokens:key-9");
    }

    @Test
    void resolve_requiresRegionOrKeyTemplate() {
        assertThatThrownBy(() -> CacheAsideAnnotationResolver.resolve(MissingRegionUser.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@CacheRegion");
    }

    @Test
    void fromAnnotationsService_resolvesKeysAndHandlesHitAndMiss() {
        var cache = new StubCache();
        var repository = new StubRepository();
        var service = CacheAsideService.fromAnnotations(
                cache, repository, RegionAnnotatedUser.class, RegionAnnotatedUser.SERIALIZER);

        cache.store.put("users:1", RegionAnnotatedUser.SERIALIZER.serialize(new RegionAnnotatedUser(1L, "cached-ana")));
        assertThat(service.get(1L))
                .hasValueSatisfying(user -> assertThat(user.name()).isEqualTo("cached-ana"));
        assertThat(repository.findByIdCount.get()).isZero();

        repository.findByIdResult = Optional.of(new RegionAnnotatedUser(2L, "loaded-bob"));
        assertThat(service.get(2L))
                .hasValueSatisfying(user -> assertThat(user.name()).isEqualTo("loaded-bob"));
        assertThat(cache.store.get("users:2")).isEqualTo("loaded-bob");
        assertThat(cache.lastPutTtl).isEqualTo(TEN_MINUTES);
    }

    @SuppressWarnings("unchecked")
    private static <ID> CacheAsideMetadata<ID> typedMetadata(Class<?> entityClass, Class<ID> idClass) {
        return (CacheAsideMetadata<ID>) CacheAsideAnnotationResolver.resolve(entityClass);
    }

    @CacheRegion("users")
    @CacheTtl(value = 10, unit = TimeUnit.MINUTES)
    static final class RegionAnnotatedUser {

        static final CacheValueSerializer<RegionAnnotatedUser> SERIALIZER = new CacheValueSerializer<>() {
            @Override
            public String serialize(RegionAnnotatedUser value) {
                return value.name();
            }

            @Override
            public RegionAnnotatedUser deserialize(String payload) {
                return new RegionAnnotatedUser(null, payload);
            }
        };

        @CacheId
        private final Long id;

        private final String name;

        RegionAnnotatedUser(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        Long getId() {
            return id;
        }

        String name() {
            return name;
        }
    }

    @CacheKey("profile:{id}")
    @CacheTtl(value = 1, unit = TimeUnit.HOURS)
    static final class TemplateAnnotatedUser {

        @CacheKey
        private final String id;

        TemplateAnnotatedUser(String id) {
            this.id = id;
        }

        String getId() {
            return id;
        }
    }

    @CacheName("orders")
    @CacheTtl(value = 5, unit = TimeUnit.MINUTES)
    static final class NamedAnnotatedUser {

        @CacheId
        private final Long id;

        NamedAnnotatedUser(Long id) {
            this.id = id;
        }

        Long getId() {
            return id;
        }
    }

    @CacheRegion("sessions")
    @CacheTtl(value = 10, unit = TimeUnit.MINUTES)
    static final class FieldTtlUser {

        @CacheId
        @CacheTtl(value = 30, unit = TimeUnit.SECONDS)
        private final Long id;

        FieldTtlUser(Long id) {
            this.id = id;
        }

        Long getId() {
            return id;
        }
    }

    @CacheRegion("tokens")
    @CacheTtl(value = 15, unit = TimeUnit.MINUTES)
    static final class GetterIdUser {

        private final String id;

        GetterIdUser(String id) {
            this.id = id;
        }

        @CacheId
        String getId() {
            return id;
        }
    }

    @CacheTtl(value = 5, unit = TimeUnit.MINUTES)
    static final class MissingRegionUser {

        @CacheId
        private final Long id;

        MissingRegionUser(Long id) {
            this.id = id;
        }
    }

    private static final class StubCache implements CacheProvider {

        final Map<String, String> store = new HashMap<>();
        Duration lastPutTtl;

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
        public void close() {}

        @Override
        public void flush() {
            store.clear();
        }

        @Override
        public void invalidate(String key) {
            store.remove(key);
        }
    }

    private static final class StubRepository implements BackingRepository<Long, RegionAnnotatedUser> {

        final AtomicInteger findByIdCount = new AtomicInteger();
        Optional<RegionAnnotatedUser> findByIdResult = Optional.empty();

        @Override
        public Optional<RegionAnnotatedUser> findById(Long id) {
            findByIdCount.incrementAndGet();
            return findByIdResult.map(user -> new RegionAnnotatedUser(id, user.name()));
        }

        @Override
        public RegionAnnotatedUser save(RegionAnnotatedUser entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(Long id) {
            throw new UnsupportedOperationException();
        }
    }
}
