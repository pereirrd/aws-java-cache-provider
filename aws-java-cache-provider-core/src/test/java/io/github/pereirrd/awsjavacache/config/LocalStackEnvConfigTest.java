package io.github.pereirrd.awsjavacache.config;

import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.ENABLED;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.HOST;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocalStackEnvConfigTest {

    @Test
    void from_defaultsToDisabledWithoutEndpoint() {
        var config = LocalStackEnvConfig.from(Map.of());
        assertThat(config.enabled()).isFalse();
        assertThat(config.host()).isEqualTo("localhost");
        assertThat(config.port()).isEqualTo(4566);
        assertThat(config.endpointUrl()).isNull();
    }

    @Test
    void from_whenEnabled_buildsGatewayEndpoint() {
        var config = LocalStackEnvConfig.from(Map.of(ENABLED, "true", HOST, "localstack"));
        assertThat(config.enabled()).isTrue();
        assertThat(config.endpointUrl()).isEqualTo(URI.create("http://localstack:4566"));
    }
}
