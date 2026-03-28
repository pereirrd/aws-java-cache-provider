package io.github.pereirrd.awsjavacache.factory;

import io.github.pereirrd.awsjavacache.core.CacheProvider;
import io.github.pereirrd.awsjavacache.core.impl.MemcachedCacheProvider;
import io.github.pereirrd.awsjavacache.core.impl.RedisCacheProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import java.io.IOException;

@ApplicationScoped
public class CacheProviderFactory {

    @Produces
    @ApplicationScoped
    @Named("redis")
    public CacheProvider redisCacheProvider() {
        var redisClient = RedisCacheClientFactory.fromEnvironment();
        return RedisCacheProvider.utf8Strings(redisClient);
    }

    @Produces
    @ApplicationScoped
    @Named("memcached")
    public CacheProvider memcachedCacheProvider() {
        try {
            var memcachedClient = MemcachedCacheClientFactory.fromEnvironment();
            return MemcachedCacheProvider.utf8Strings(memcachedClient);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Memcached client from environment", e);
        }
    }
}
