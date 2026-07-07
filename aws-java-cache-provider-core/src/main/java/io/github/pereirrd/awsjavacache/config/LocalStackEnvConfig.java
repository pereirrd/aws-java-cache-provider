package io.github.pereirrd.awsjavacache.config;

import static io.github.pereirrd.awsjavacache.constants.LocalStackConstants.DEFAULT_ENDPOINT_SCHEME;
import static io.github.pereirrd.awsjavacache.constants.LocalStackConstants.DEFAULT_HOST;
import static io.github.pereirrd.awsjavacache.constants.LocalStackConstants.DEFAULT_PORT;
import static io.github.pereirrd.awsjavacache.constants.LocalStackConstants.DEFAULT_SERVICES;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.DEBUG;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.ENABLED;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.HOST;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.PERSISTENCE;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.PORT;
import static io.github.pereirrd.awsjavacache.constants.LocalStackEnvKeys.SERVICES;

import io.github.pereirrd.awsjavacache.util.CacheEnvSupport;
import java.net.URI;
import java.util.Map;

/**
 * LocalStack runtime settings for local AWS emulation (Docker Compose). Data-plane traffic for Redis/Memcached still
 * uses {@link RedisCacheEnvConfig} / {@link MemcachedCacheEnvConfig} against the companion containers.
 */
public record LocalStackEnvConfig(
        boolean enabled, String host, int port, String services, boolean debug, boolean persistence, URI endpointUrl) {

    public static LocalStackEnvConfig fromEnvironment() {
        return from(System.getenv());
    }

    public static LocalStackEnvConfig from(Map<String, String> env) {
        var enabled = CacheEnvSupport.parseBoolean(env, ENABLED, false);
        var host = CacheEnvSupport.optional(env, HOST);
        if (host == null || host.isBlank()) {
            host = DEFAULT_HOST;
        }
        var port = CacheEnvSupport.parseInt(env, PORT, DEFAULT_PORT);
        var services = CacheEnvSupport.optional(env, SERVICES);
        if (services == null || services.isBlank()) {
            services = DEFAULT_SERVICES;
        }
        var debug = CacheEnvSupport.parseBoolean(env, DEBUG, false);
        var persistence = CacheEnvSupport.parseBoolean(env, PERSISTENCE, false);
        var endpointUrl = enabled ? URI.create(DEFAULT_ENDPOINT_SCHEME + "://" + host + ":" + port) : null;

        return new LocalStackEnvConfig(enabled, host, port, services, debug, persistence, endpointUrl);
    }
}
