package io.github.pereirrd.awsjavacache.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pereirrd.awsjavacache.config.RedisCacheEnvConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedisCacheFactoryTest {

  @Test
  void toRedisUri_reflectsPasswordOnlyAuth() {
    var env =
        Map.of(
            RedisCacheEnvConfig.Keys.HOST, "localhost",
            RedisCacheEnvConfig.Keys.PASSWORD, "only-password");
    var config = RedisCacheEnvConfig.from(env);
    var uri = RedisCacheClientFactory.toRedisUri(config);
    assertThat(uri.getHost()).isEqualTo("localhost");
    assertThat(new String(uri.getPassword())).isEqualTo("only-password");
    assertThat(uri.getUsername()).isNull();
  }

  @Test
  void from_buildsLettuceClient() {
    var env = Map.of(RedisCacheEnvConfig.Keys.HOST, "127.0.0.1");
    var client = RedisCacheClientFactory.from(RedisCacheEnvConfig.from(env));
    try (client) {
      assertThat(client).isNotNull();
    }
  }
}
