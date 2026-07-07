package io.github.pereirrd.awsjavacache.integration;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class IntegrationTestConstants {

    static final String LOCALSTACK_IMAGE = "localstack/localstack:4.4";
    static final String REDIS_IMAGE = "redis:7.4-alpine";
    static final String SECRET_NAME = "aws-java-cache/local/redis-password";
    static final String SECRET_VALUE = "integration-test-password";
}
