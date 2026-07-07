package io.github.pereirrd.awsjavacache.factory;

import io.github.pereirrd.awsjavacache.config.AwsSdkEnvConfig;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.elasticache.ElastiCacheClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.sts.StsClient;

public final class AwsSdkClientFactory {

    private AwsSdkClientFactory() {}

    public static AwsSdkEnvConfig configFromEnvironment() {
        return AwsSdkEnvConfig.fromEnvironment();
    }

    public static ElastiCacheClient elasticache() {
        return elasticache(configFromEnvironment());
    }

    public static ElastiCacheClient elasticache(AwsSdkEnvConfig config) {
        return build(ElastiCacheClient.builder(), config);
    }

    public static SecretsManagerClient secretsManager() {
        return secretsManager(configFromEnvironment());
    }

    public static SecretsManagerClient secretsManager(AwsSdkEnvConfig config) {
        return build(SecretsManagerClient.builder(), config);
    }

    public static CloudWatchClient cloudWatch() {
        return cloudWatch(configFromEnvironment());
    }

    public static CloudWatchClient cloudWatch(AwsSdkEnvConfig config) {
        return build(CloudWatchClient.builder(), config);
    }

    public static StsClient sts() {
        return sts(configFromEnvironment());
    }

    public static StsClient sts(AwsSdkEnvConfig config) {
        return build(StsClient.builder(), config);
    }

    private static <B extends AwsClientBuilder<B, C>, C> C build(B builder, AwsSdkEnvConfig config) {
        var credentials = AwsBasicCredentials.create(config.accessKeyId(), config.secretAccessKey());
        builder.region(Region.of(config.region())).credentialsProvider(StaticCredentialsProvider.create(credentials));
        if (config.endpointOverride() != null) {
            builder.endpointOverride(config.endpointOverride());
        }
        return builder.build();
    }
}
