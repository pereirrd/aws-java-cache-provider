package io.github.pereirrd.awsjavacache.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LocalStackTraceConstants {

    public static final String SPAN_LOCALSTACK_BOOTSTRAP = "localstack.bootstrap";
    public static final String SPAN_AWS_SDK_CLIENT = "aws.sdk.client";
}
