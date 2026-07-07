package io.github.pereirrd.awsjavacache.integration;

import io.github.pereirrd.awsjavacache.config.AwsSdkEnvConfig;
import io.github.pereirrd.awsjavacache.config.RedisCacheEnvConfig;
import io.github.pereirrd.awsjavacache.factory.AwsSdkClientFactory;
import io.github.pereirrd.awsjavacache.factory.RedisCacheClientFactory;

/**
 * Detects a running {@code docker compose} stack (LocalStack + Redis on localhost). Used by {@code *ComposeIT}
 * tests that do not require Docker API access — only TCP to the published ports.
 */
public final class ComposeIntegrationSupport {

    /** Secret created by {@code localstack/init/ready.d/01-bootstrap.sh}. */
    public static final String BOOTSTRAP_SECRET_VALUE = "local-dev-password";

    private ComposeIntegrationSupport() {}

    public static boolean isStackAvailable() {
        return isLocalStackReachable() && isRedisReachable();
    }

    public static boolean isLocalStackAvailable() {
        return isLocalStackReachable();
    }

    public static boolean isRedisAvailable() {
        return isRedisReachable();
    }

    public static AwsSdkEnvConfig awsConfigFromEnvironment() {
        return AwsSdkEnvConfig.fromEnvironment();
    }

    public static RedisCacheEnvConfig redisConfigFromEnvironment() {
        return RedisCacheEnvConfig.fromEnvironment();
    }

    private static boolean isLocalStackReachable() {
        try {
            var config = awsConfigFromEnvironment();
            if (!config.localstackEnabled()) {
                return false;
            }
            try (var sts = AwsSdkClientFactory.sts(config)) {
                sts.getCallerIdentity();
                return true;
            }
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isRedisReachable() {
        for (var attempt = 0; attempt < 5; attempt++) {
            if (pingRedis()) {
                return true;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean pingRedis() {
        try {
            var client = RedisCacheClientFactory.from(redisConfigFromEnvironment());
            try (client) {
                var connection = client.connect();
                try (connection) {
                    return "PONG".equals(connection.sync().ping());
                }
            }
        } catch (RuntimeException e) {
            return false;
        }
    }
}
