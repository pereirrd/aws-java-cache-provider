package io.github.pereirrd.awsjavacache.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AwsSdkEnvKeys {

    public static final String REGION = "AWS_JAVA_CACHE_AWS_REGION";
    public static final String ENDPOINT_URL = "AWS_JAVA_CACHE_AWS_ENDPOINT_URL";
    public static final String ACCESS_KEY_ID = "AWS_ACCESS_KEY_ID";
    public static final String SECRET_ACCESS_KEY = "AWS_SECRET_ACCESS_KEY";
    public static final String DEFAULT_REGION = "AWS_DEFAULT_REGION";
    public static final String STANDARD_ENDPOINT_URL = "AWS_ENDPOINT_URL";
}
