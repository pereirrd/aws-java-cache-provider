package io.github.pereirrd.awsjavacache.integration;

import io.github.pereirrd.awsjavacache.config.RedisCacheEnvConfig;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

final class RedisTestSupport {

    private RedisTestSupport() {}

    static GenericContainer<?> newRedisContainer() {
        return new GenericContainer<>(DockerImageName.parse(IntegrationTestConstants.REDIS_IMAGE))
                .withExposedPorts(6379);
    }

    static Map<String, String> redisEnvironment(GenericContainer<?> redis) {
        var env = new HashMap<String, String>();
        env.put(RedisCacheEnvConfig.HOST, redis.getHost());
        env.put(RedisCacheEnvConfig.PORT, String.valueOf(redis.getMappedPort(6379)));
        env.put(RedisCacheEnvConfig.TLS, "false");
        env.put(RedisCacheEnvConfig.DATABASE, "0");
        return Map.copyOf(env);
    }

    static RedisCacheEnvConfig redisConfig(GenericContainer<?> redis) {
        return RedisCacheEnvConfig.from(redisEnvironment(redis));
    }
}
