package io.github.pereirrd.awsjavacache.config;

import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.ACCESS_KEY_ID;
import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.DEFAULT_REGION;
import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.ENDPOINT_URL;
import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.SECRET_ACCESS_KEY;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.ENABLED;
import static io.github.pereirrd.awsjavacache.constants.LocalStackErrorMessages.ACCESS_KEY_REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AwsSdkEnvConfigTest {

    @Test
    void from_whenLocalStackEnabled_usesTestCredentialsAndEndpoint() {
        var config = AwsSdkEnvConfig.from(Map.of(ENABLED, "true"));
        assertThat(config.localstackEnabled()).isTrue();
        assertThat(config.accessKeyId()).isEqualTo("test");
        assertThat(config.secretAccessKey()).isEqualTo("test");
        assertThat(config.region()).isEqualTo("us-east-1");
        assertThat(config.endpointOverride()).isEqualTo(URI.create("http://localhost:4566"));
    }

    @Test
    void from_whenLocalStackDisabled_requiresExplicitCredentials() {
        assertThatThrownBy(() -> AwsSdkEnvConfig.from(Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ACCESS_KEY_REQUIRED);
    }

    @Test
    void from_whenLocalStackDisabled_readsProductionCredentials() {
        var config = AwsSdkEnvConfig.from(Map.of(
                ENABLED, "false",
                ACCESS_KEY_ID, "AKIA",
                SECRET_ACCESS_KEY, "secret",
                DEFAULT_REGION, "sa-east-1"));
        assertThat(config.localstackEnabled()).isFalse();
        assertThat(config.accessKeyId()).isEqualTo("AKIA");
        assertThat(config.secretAccessKey()).isEqualTo("secret");
        assertThat(config.region()).isEqualTo("sa-east-1");
        assertThat(config.endpointOverride()).isNull();
    }

    @Test
    void from_honoursExplicitEndpointOverride() {
        var config = AwsSdkEnvConfig.from(Map.of(ENABLED, "true", ENDPOINT_URL, "http://127.0.0.1:4566"));
        assertThat(config.endpointOverride()).isEqualTo(URI.create("http://127.0.0.1:4566"));
    }
}
