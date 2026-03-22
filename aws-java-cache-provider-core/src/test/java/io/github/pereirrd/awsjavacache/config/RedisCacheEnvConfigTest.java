package io.github.pereirrd.awsjavacache.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedisCacheEnvConfigTest {

  @Test
  void from_requiresHost() {
    assertThatThrownBy(() -> RedisCacheEnvConfig.from(Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(RedisCacheEnvConfig.Keys.HOST);
  }

  @Test
  void from_appliesElastiCacheRedisDefaults() {
    var env = new HashMap<String, String>();
    env.put(RedisCacheEnvConfig.Keys.HOST, "master.prod.abc123.use1.cache.amazonaws.com");
    var config = RedisCacheEnvConfig.from(env);
    assertThat(config.host()).isEqualTo("master.prod.abc123.use1.cache.amazonaws.com");
    assertThat(config.port()).isEqualTo(6379);
    assertThat(config.tls()).isFalse();
    assertThat(config.database()).isZero();
    assertThat(config.commandTimeout()).isEmpty();
    assertThat(config.username()).isNull();
    assertThat(config.password()).isNull();
  }

  @Test
  void from_readsTlsAclAndTimeoutForEncryptedClusterEndpoint() {
    var env = Map.of(
        RedisCacheEnvConfig.Keys.HOST, "clustercfg.prod.abc123.use1.cache.amazonaws.com",
        RedisCacheEnvConfig.Keys.PORT, "6379",
        RedisCacheEnvConfig.Keys.TLS, "true",
        RedisCacheEnvConfig.Keys.USERNAME, "app-user",
        RedisCacheEnvConfig.Keys.PASSWORD, "rotated-secret",
        RedisCacheEnvConfig.Keys.DATABASE, "0",
        RedisCacheEnvConfig.Keys.TIMEOUT_MS, "2000");
    var config = RedisCacheEnvConfig.from(env);
    assertThat(config.tls()).isTrue();
    assertThat(config.username()).isEqualTo("app-user");
    assertThat(config.password()).isEqualTo("rotated-secret");
    assertThat(config.commandTimeout()).contains(Duration.ofMillis(2000));
  }
}
