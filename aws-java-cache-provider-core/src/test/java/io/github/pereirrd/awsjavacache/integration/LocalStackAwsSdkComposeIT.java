package io.github.pereirrd.awsjavacache.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.pereirrd.awsjavacache.factory.AwsSdkClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

class LocalStackAwsSdkComposeIT {

    @BeforeEach
    void requireLocalStack() {
        assumeTrue(
                ComposeIntegrationSupport.isLocalStackAvailable(),
                "LocalStack not reachable — run: docker compose up -d");
    }

    @Test
    void sts_getCallerIdentity_returnsLocalStackAccount() {
        var config = ComposeIntegrationSupport.awsConfigFromEnvironment();
        try (var sts = AwsSdkClientFactory.sts(config)) {
            var identity = sts.getCallerIdentity();
            assertThat(identity.account()).isEqualTo("000000000000");
            assertThat(identity.userId()).isNotBlank();
        }
    }

    @Test
    void secretsManager_readsBootstrapSecretFromComposeInit() {
        var config = ComposeIntegrationSupport.awsConfigFromEnvironment();
        try (var secretsManager = AwsSdkClientFactory.secretsManager(config)) {
            var response = secretsManager.getSecretValue(GetSecretValueRequest.builder()
                    .secretId(IntegrationTestConstants.SECRET_NAME)
                    .build());
            assertThat(response.secretString()).isEqualTo(ComposeIntegrationSupport.BOOTSTRAP_SECRET_VALUE);
        }
    }

    @Test
    void cloudWatch_listMetrics_doesNotFail() {
        var config = ComposeIntegrationSupport.awsConfigFromEnvironment();
        try (var cloudWatch = AwsSdkClientFactory.cloudWatch(config)) {
            var response = cloudWatch.listMetrics();
            assertThat(response).isNotNull();
        }
    }
}
