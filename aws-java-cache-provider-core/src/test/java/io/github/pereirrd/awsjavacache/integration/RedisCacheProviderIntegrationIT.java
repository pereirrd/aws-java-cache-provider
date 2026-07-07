package io.github.pereirrd.awsjavacache.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pereirrd.awsjavacache.core.impl.RedisCacheProvider;
import io.github.pereirrd.awsjavacache.factory.RedisCacheClientFactory;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisCacheProviderIntegrationIT {

    @Container
    static final GenericContainer<?> REDIS = RedisTestSupport.newRedisContainer();

    private RedisCacheProvider provider;

    @BeforeEach
    void setUp() {
        var redisClient = RedisCacheClientFactory.from(RedisTestSupport.redisConfig(REDIS));
        provider = RedisCacheProvider.utf8Strings(redisClient);
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.close();
        }
    }

    @Test
    void putAndGet_roundTripValue() {
        provider.put("integration:key", "value");
        assertThat(provider.get("integration:key")).isEqualTo("value");
    }

    @Test
    void remove_deletesKey() {
        provider.put("integration:remove", "gone");
        provider.remove("integration:remove");
        assertThat(provider.get("integration:remove")).isNull();
    }

    @Test
    void putWithTtl_expiresEntry() throws InterruptedException {
        provider.put("integration:ttl", "short-lived", Duration.ofSeconds(1));
        assertThat(provider.get("integration:ttl")).isEqualTo("short-lived");
        Thread.sleep(1_200L);
        assertThat(provider.get("integration:ttl")).isNull();
    }
}
