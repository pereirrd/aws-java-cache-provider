package io.github.pereirrd.awsjavacache.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.pereirrd.awsjavacache.core.impl.MemcachedCacheProvider;
import io.github.pereirrd.awsjavacache.factory.MemcachedCacheClientFactory;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemcachedCacheProviderComposeIT {

    private MemcachedCacheProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        assumeTrue(
                ComposeIntegrationSupport.isMemcachedAvailable(),
                "Memcached not reachable — run: docker compose --profile memcached up -d"
                        + " and set AWS_JAVA_CACHE_MEMCACHED_NODES in .env");
        var memcachedClient =
                MemcachedCacheClientFactory.from(ComposeIntegrationSupport.memcachedConfigFromEnvironment());
        provider = MemcachedCacheProvider.utf8Strings(memcachedClient);
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
        provider.put("compose:memcached:key", "value");
        assertThat(provider.get("compose:memcached:key")).isEqualTo("value");
    }

    @Test
    void invalidate_deletesKey() {
        provider.put("compose:memcached:invalidate", "gone");
        provider.invalidate("compose:memcached:invalidate");
        assertThat(provider.get("compose:memcached:invalidate")).isNull();
    }
}
