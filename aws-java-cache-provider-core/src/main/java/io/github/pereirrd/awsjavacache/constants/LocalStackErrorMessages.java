package io.github.pereirrd.awsjavacache.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocalStackErrorMessages {

    public static final String INVALID_ENDPOINT_URL = "Invalid LocalStack/AWS endpoint URL";
    public static final String ACCESS_KEY_REQUIRED =
            "AWS_ACCESS_KEY_ID is required when LocalStack is disabled and no default credential chain is configured";
    public static final String SECRET_KEY_REQUIRED =
            "AWS_SECRET_ACCESS_KEY is required when LocalStack is disabled and no default credential chain is configured";
}
