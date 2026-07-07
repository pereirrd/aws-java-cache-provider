package io.github.pereirrd.awsjavacache.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.pereirrd.awsjavacache.core.impl.RedisCacheProvider;
import io.github.pereirrd.awsjavacache.factory.RedisCacheClientFactory;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisCacheProviderComposeIT {

    private RedisCacheProvider provider;

    @BeforeEach
    void setUp() {
        assumeTrue(ComposeIntegrationSupport.isRedisAvailable(), "Redis not reachable — run: docker compose up -d");
        var redisClient = RedisCacheClientFactory.from(ComposeIntegrationSupport.redisConfigFromEnvironment());
        provider = RedisCacheProvider.utf8Strings(redisClient);
        provider.flush();
    }

    @AfterEach
    void tearDown() {
        if (provider != null) {
            provider.close();
        }
    }

    @Test
    void putAndGet_roundTripValue() {
        provider.put("compose:integration:key", "value");
        assertThat(provider.get("compose:integration:key")).isEqualTo("value");
    }

    @Test
    void remove_deletesKey() {
        provider.put("compose:integration:remove", "gone");
        provider.remove("compose:integration:remove");
        assertThat(provider.get("compose:integration:remove")).isNull();
    }

    @Test
    void putWithTtl_expiresEntry() throws InterruptedException {
        provider.put("compose:integration:ttl", "short-lived", Duration.ofSeconds(1));
        assertThat(provider.get("compose:integration:ttl")).isEqualTo("short-lived");
        Thread.sleep(1_200L);
        assertThat(provider.get("compose:integration:ttl")).isNull();
    }
}
