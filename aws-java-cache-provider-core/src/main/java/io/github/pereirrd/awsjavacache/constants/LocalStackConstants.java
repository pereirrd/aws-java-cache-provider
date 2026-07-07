package io.github.pereirrd.awsjavacache.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocalStackConstants {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 4566;
    public static final String DEFAULT_ENDPOINT_SCHEME = "http";
    public static final String DEFAULT_SERVICES = "elasticache,secretsmanager,cloudwatch,sts";
}
