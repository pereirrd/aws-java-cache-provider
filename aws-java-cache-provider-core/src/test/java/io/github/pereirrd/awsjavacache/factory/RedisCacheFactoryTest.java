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
        assertThat(uri.getHost()).isEqualTo(config.host());
        var creds = ((RedisCredentialsProvider.ImmediateRedisCredentialsProvider) uri.getCredentialsProvider())
                .resolveCredentialsNow();
        assertThat(new String(creds.getPassword())).isEqualTo(config.password());
        assertThat(creds.getUsername()).isNull();
        assertThat(uri.getPort()).isEqualTo(config.port());
        assertThat(uri.isSsl()).isEqualTo(config.tls());
        assertThat(uri.getDatabase()).isEqualTo(config.database());
        assertThat(uri.getTimeout()).isEqualTo(config.commandTimeout());
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
