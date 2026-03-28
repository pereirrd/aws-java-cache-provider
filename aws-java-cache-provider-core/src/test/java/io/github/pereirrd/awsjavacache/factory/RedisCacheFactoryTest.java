package io.github.pereirrd.awsjavacache.factory;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pereirrd.awsjavacache.config.RedisCacheEnvConfig;
import io.lettuce.core.RedisCredentialsProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedisCacheFactoryTest {

    @Test
    void toRedisUri_reflectsPasswordOnlyAuth() {
        var env = Map.of(
                RedisCacheEnvConfig.HOST, "localhost",
                RedisCacheEnvConfig.PASSWORD, "only-password");
        var config = RedisCacheEnvConfig.from(env);
        var uri = RedisCacheClientFactory.toRedisUri(config);
        assertThat(uri.getHost()).isEqualTo(config.getHost());
        var creds =
                ((RedisCredentialsProvider.ImmediateRedisCredentialsProvider) uri.getCredentialsProvider())
                        .resolveCredentialsNow();
        assertThat(new String(creds.getPassword())).isEqualTo(config.getPassword());
        assertThat(creds.getUsername()).isNull();
        assertThat(uri.getPort()).isEqualTo(config.getPort());
        assertThat(uri.isSsl()).isEqualTo(config.isTls());
        assertThat(uri.getDatabase()).isEqualTo(config.getDatabase());
        assertThat(uri.getTimeout()).isEqualTo(config.getCommandTimeout());
    }

    @Test
    void from_buildsLettuceClient() {
        var env = Map.of(RedisCacheEnvConfig.HOST, "127.0.0.1");
        var client = RedisCacheClientFactory.from(RedisCacheEnvConfig.from(env));
        try (client) {
            assertThat(client).isNotNull();
        }
    }
}
