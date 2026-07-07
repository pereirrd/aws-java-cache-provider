package io.github.pereirrd.awsjavacache.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.pereirrd.awsjavacache.factory.AwsSdkClientFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.localstack.LocalStackContainer;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

@Testcontainers(disabledWithoutDocker = true)
class LocalStackAwsSdkIntegrationIT {

    @Container
    static final LocalStackContainer LOCALSTACK = LocalStackTestSupport.newLocalStackContainer();

    @BeforeAll
    static void bootstrapSecretsManager() {
        var config = LocalStackTestSupport.awsConfig(LOCALSTACK);
        try (var secretsManager = AwsSdkClientFactory.secretsManager(config)) {
            secretsManager.createSecret(CreateSecretRequest.builder()
                    .name(IntegrationTestConstants.SECRET_NAME)
                    .description("Bootstrap secret for integration tests")
                    .secretString(IntegrationTestConstants.SECRET_VALUE)
                    .build());
        }
    }

    @Test
    void sts_getCallerIdentity_returnsLocalStackAccount() {
        var config = LocalStackTestSupport.awsConfig(LOCALSTACK);
        try (var sts = AwsSdkClientFactory.sts(config)) {
            var identity = sts.getCallerIdentity();
            assertThat(identity.account()).isEqualTo("000000000000");
            assertThat(identity.userId()).isNotBlank();
        }
    }

    @Test
    void secretsManager_readsBootstrapSecret() {
        var config = LocalStackTestSupport.awsConfig(LOCALSTACK);
        try (var secretsManager = AwsSdkClientFactory.secretsManager(config)) {
            var response = secretsManager.getSecretValue(GetSecretValueRequest.builder()
                    .secretId(IntegrationTestConstants.SECRET_NAME)
                    .build());
            assertThat(response.secretString()).isEqualTo(IntegrationTestConstants.SECRET_VALUE);
        }
    }

    @Test
    void cloudWatch_listMetrics_doesNotFail() {
        var config = LocalStackTestSupport.awsConfig(LOCALSTACK);
        try (var cloudWatch = AwsSdkClientFactory.cloudWatch(config)) {
            var response = cloudWatch.listMetrics();
            assertThat(response).isNotNull();
        }
    }
}
