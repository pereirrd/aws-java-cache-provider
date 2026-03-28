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
        .hasMessageContaining(RedisCacheEnvConfig.HOST);
  }

  @Test
  void from_appliesElastiCacheRedisDefaults() {
    var env = new HashMap<String, String>();
    env.put(RedisCacheEnvConfig.HOST, "master.prod.abc123.use1.cache.amazonaws.com");
    var config = RedisCacheEnvConfig.from(env);
    assertThat(config.getHost()).isEqualTo("master.prod.abc123.use1.cache.amazonaws.com");
    assertThat(config.getPort()).isEqualTo(6379);
    assertThat(config.isTls()).isFalse();
    assertThat(config.getDatabase()).isZero();
    assertThat(config.getCommandTimeout()).isEqualTo(Duration.ZERO);
    assertThat(config.getUsername()).isNull();
    assertThat(config.getPassword()).isNull();
  }

  @Test
  void from_readsTlsAclAndTimeoutForEncryptedClusterEndpoint() {
    var env =
        Map.of(
            RedisCacheEnvConfig.HOST, "clustercfg.prod.abc123.use1.cache.amazonaws.com",
            RedisCacheEnvConfig.PORT, "6379",
            RedisCacheEnvConfig.TLS, "true",
            RedisCacheEnvConfig.USERNAME, "app-user",
            RedisCacheEnvConfig.PASSWORD, "rotated-secret",
            RedisCacheEnvConfig.DATABASE, "0",
            RedisCacheEnvConfig.TIMEOUT_MS, "2000");
    var config = RedisCacheEnvConfig.from(env);
    assertThat(config.isTls()).isTrue();
    assertThat(config.getUsername()).isEqualTo("app-user");
    assertThat(config.getPassword()).isEqualTo("rotated-secret");
    assertThat(config.getCommandTimeout()).isEqualTo(Duration.ofMillis(2000));
  }
}
