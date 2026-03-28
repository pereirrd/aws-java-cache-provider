package io.github.pereirrd.awsjavacache.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MemcachedCacheEnvConfigTest {

    @Test
    void from_requiresNodes() {
        assertThatThrownBy(() -> MemcachedCacheEnvConfig.from(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(MemcachedCacheEnvConfig.Keys.NODES);
    }

    @Test
    void from_parsesMultiNodeConfigurationLineFromElastiCacheConfigEndpoint() {
        var env = Map.of(
                MemcachedCacheEnvConfig.Keys.NODES,
                "memcached-001.prod.abc123.cfg.use1.cache.amazonaws.com:11211"
                        + " memcached-002.prod.abc123.cfg.use1.cache.amazonaws.com:11211");
        var config = MemcachedCacheEnvConfig.from(env);
        assertThat(config.nodes()).contains("memcached-001").contains("11211");
        assertThat(config.operationTimeoutMillis()).isEqualTo(MemcachedCacheEnvConfig.DEFAULT_OPERATION_TIMEOUT_MS);
    }

    @Test
    void from_rejectsNonPositiveOperationTimeout() {
        var env = Map.of(
                MemcachedCacheEnvConfig.Keys.NODES,
                "memcached-001.prod.abc123.cfg.use1.cache.amazonaws.com:11211",
                MemcachedCacheEnvConfig.Keys.OPERATION_TIMEOUT_MS,
                "0");
        assertThatThrownBy(() -> MemcachedCacheEnvConfig.from(env))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(MemcachedCacheEnvConfig.Keys.OPERATION_TIMEOUT_MS);
    }
}
