package io.github.pereirrd.awsjavacache.config;

import io.github.pereirrd.awsjavacache.util.CacheEnvSupport;
import java.time.Duration;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class RedisCacheEnvConfig {

    public static final String HOST = "AWS_JAVA_CACHE_REDIS_HOST";
    public static final String PORT = "AWS_JAVA_CACHE_REDIS_PORT";
    public static final String USERNAME = "AWS_JAVA_CACHE_REDIS_USERNAME";
    public static final String PASSWORD = "AWS_JAVA_CACHE_REDIS_PASSWORD";
    public static final String TLS = "AWS_JAVA_CACHE_REDIS_TLS";
    public static final String DATABASE = "AWS_JAVA_CACHE_REDIS_DATABASE";
    public static final String TIMEOUT_MS = "AWS_JAVA_CACHE_REDIS_TIMEOUT_MS";

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final boolean tls;
    private final int database;
    private final Duration commandTimeout;

    public static RedisCacheEnvConfig fromEnvironment() {
        return from(System.getenv());
    }

    public static RedisCacheEnvConfig from(Map<String, String> env) {
        var host = CacheEnvSupport.required(env, HOST);
        var port = CacheEnvSupport.parseInt(env, PORT, 6379);
        var username = CacheEnvSupport.optional(env, USERNAME);
        var password = CacheEnvSupport.optional(env, PASSWORD);
        var tls = CacheEnvSupport.parseBoolean(env, TLS, false);
        var database = CacheEnvSupport.parseInt(env, DATABASE, 0);
        var timeoutMs = CacheEnvSupport.parseLong(env, TIMEOUT_MS, -1L);
        var commandTimeout = timeoutMs < 0 ? Duration.ZERO : Duration.ofMillis(timeoutMs);

        return new RedisCacheEnvConfig(host, port, username, password, tls, database, commandTimeout);
    }
}
