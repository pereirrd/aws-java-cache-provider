package io.github.pereirrd.awsjavacache.factory;

import io.github.pereirrd.awsjavacache.config.RedisCacheEnvConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;

public final class RedisCacheClientFactory {

    private RedisCacheClientFactory() {}

    public static RedisClient fromEnvironment() {
        return from(RedisCacheEnvConfig.fromEnvironment());
    }

    public static RedisClient from(RedisCacheEnvConfig config) {
        return RedisClient.create(toRedisUri(config));
    }

    static RedisURI toRedisUri(RedisCacheEnvConfig config) {
        var builder = RedisURI.builder()
                .withHost(config.getHost())
                .withPort(config.getPort())
                .withSsl(config.isTls())
                .withDatabase(config.getDatabase());

        builder.withTimeout(config.getCommandTimeout());
        var user = config.getUsername();
        var pass = config.getPassword();

        if (user != null && !user.isBlank()) {
            builder.withAuthentication(user, pass);
        } else if (pass != null && !pass.isBlank()) {
            builder.withPassword(pass.toCharArray());
        }

        return builder.build();
    }
}
