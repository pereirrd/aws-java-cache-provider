package io.github.pereirrd.awsjavacache.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocalStackEnvKeys {

    public static final String ENABLED = "AWS_JAVA_CACHE_LOCALSTACK_ENABLED";
    public static final String HOST = "AWS_JAVA_CACHE_LOCALSTACK_HOST";
    public static final String PORT = "AWS_JAVA_CACHE_LOCALSTACK_PORT";
    public static final String SERVICES = "AWS_JAVA_CACHE_LOCALSTACK_SERVICES";
    public static final String DEBUG = "AWS_JAVA_CACHE_LOCALSTACK_DEBUG";
    public static final String PERSISTENCE = "AWS_JAVA_CACHE_LOCALSTACK_PERSISTENCE";
}
