package io.github.pereirrd.awsjavacache.config;

import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.ACCESS_KEY_ID;
import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.DEFAULT_REGION;
import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.ENDPOINT_URL;
import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.REGION;
import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.SECRET_ACCESS_KEY;
import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.STANDARD_ENDPOINT_URL;
import static io.github.pereirrd.awsjavacache.constants.LocalStackErrorMessages.ACCESS_KEY_REQUIRED;
import static io.github.pereirrd.awsjavacache.constants.LocalStackErrorMessages.INVALID_ENDPOINT_URL;
import static io.github.pereirrd.awsjavacache.constants.LocalStackErrorMessages.SECRET_KEY_REQUIRED;

import io.github.pereirrd.awsjavacache.util.CacheEnvSupport;
import java.net.URI;
import java.util.Map;

/**
 * AWS SDK v2 client settings resolved from environment variables. When {@link LocalStackEnvConfig#enabled()} is
 * {@code true}, defaults credentials to {@code test/test} and endpoint to the LocalStack gateway unless overridden.
 */
public record AwsSdkEnvConfig(
        String region, String accessKeyId, String secretAccessKey, URI endpointOverride, boolean localstackEnabled) {

    private static final String LOCALSTACK_DEV_ACCESS_KEY = "test";
    private static final String LOCALSTACK_DEV_SECRET_KEY = "test";
    private static final String FALLBACK_REGION = "us-east-1";

    public static AwsSdkEnvConfig fromEnvironment() {
        return from(System.getenv());
    }

    public static AwsSdkEnvConfig from(Map<String, String> env) {
        var localStack = LocalStackEnvConfig.from(env);
        var region = resolveRegion(env);
        var accessKeyId = resolveAccessKeyId(env, localStack.enabled());
        var secretAccessKey = resolveSecretAccessKey(env, localStack.enabled());
        var endpointOverride = resolveEndpointOverride(env, localStack);

        return new AwsSdkEnvConfig(region, accessKeyId, secretAccessKey, endpointOverride, localStack.enabled());
    }

    private static String resolveRegion(Map<String, String> env) {
        var region = CacheEnvSupport.optional(env, REGION);
        if (region == null || region.isBlank()) {
            region = CacheEnvSupport.optional(env, DEFAULT_REGION);
        }
        if (region == null || region.isBlank()) {
            return FALLBACK_REGION;
        }
        return region;
    }

    private static String resolveAccessKeyId(Map<String, String> env, boolean localstackEnabled) {
        var accessKeyId = CacheEnvSupport.optional(env, ACCESS_KEY_ID);
        if (accessKeyId == null || accessKeyId.isBlank()) {
            if (localstackEnabled) {
                return LOCALSTACK_DEV_ACCESS_KEY;
            }
            throw new IllegalArgumentException(ACCESS_KEY_REQUIRED);
        }
        return accessKeyId;
    }

    private static String resolveSecretAccessKey(Map<String, String> env, boolean localstackEnabled) {
        var secretAccessKey = CacheEnvSupport.optional(env, SECRET_ACCESS_KEY);
        if (secretAccessKey == null || secretAccessKey.isBlank()) {
            if (localstackEnabled) {
                return LOCALSTACK_DEV_SECRET_KEY;
            }
            throw new IllegalArgumentException(SECRET_KEY_REQUIRED);
        }
        return secretAccessKey;
    }

    private static URI resolveEndpointOverride(Map<String, String> env, LocalStackEnvConfig localStack) {
        var explicit = firstNonBlank(
                CacheEnvSupport.optional(env, ENDPOINT_URL), CacheEnvSupport.optional(env, STANDARD_ENDPOINT_URL));
        if (explicit != null) {
            return parseEndpoint(explicit);
        }
        return localStack.enabled() ? localStack.endpointUrl() : null;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private static URI parseEndpoint(String raw) {
        try {
            return URI.create(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(INVALID_ENDPOINT_URL + ": " + raw, e);
        }
    }
}
