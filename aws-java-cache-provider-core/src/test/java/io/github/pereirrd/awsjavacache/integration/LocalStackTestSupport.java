package io.github.pereirrd.awsjavacache.integration;

import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.ACCESS_KEY_ID;
import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.DEFAULT_REGION;
import static io.github.pereirrd.awsjavacache.constants.AwsSdkEnvKeys.SECRET_ACCESS_KEY;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.ENABLED;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.HOST;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.PORT;

import io.github.pereirrd.awsjavacache.config.AwsSdkEnvConfig;
import io.github.pereirrd.awsjavacache.config.LocalStackEnvConfig;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

final class LocalStackTestSupport {

    private LocalStackTestSupport() {}

    static LocalStackContainer newLocalStackContainer() {
        var container = new LocalStackContainer(DockerImageName.parse(IntegrationTestConstants.LOCALSTACK_IMAGE))
                .withServices("secretsmanager", "sts", "cloudwatch");
        var authToken = System.getenv("LOCALSTACK_AUTH_TOKEN");
        if (authToken != null && !authToken.isBlank()) {
            container.withEnv("LOCALSTACK_AUTH_TOKEN", authToken);
        }
        return container;
    }

    static Map<String, String> awsEnvironment(LocalStackContainer localstack) {
        var env = new HashMap<String, String>();
        env.put(ENABLED, "true");
        env.put(HOST, localstack.getHost());
        env.put(PORT, String.valueOf(localstack.getMappedPort(4566)));
        env.put(ACCESS_KEY_ID, localstack.getAccessKey());
        env.put(SECRET_ACCESS_KEY, localstack.getSecretKey());
        env.put(DEFAULT_REGION, localstack.getRegion());
        return Map.copyOf(env);
    }

    static AwsSdkEnvConfig awsConfig(LocalStackContainer localstack) {
        return AwsSdkEnvConfig.from(awsEnvironment(localstack));
    }

    static LocalStackEnvConfig localStackConfig(LocalStackContainer localstack) {
        return LocalStackEnvConfig.from(awsEnvironment(localstack));
    }
}
