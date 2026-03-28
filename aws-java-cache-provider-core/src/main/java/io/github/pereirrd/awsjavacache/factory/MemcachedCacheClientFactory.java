package io.github.pereirrd.awsjavacache.factory;

import io.github.pereirrd.awsjavacache.config.MemcachedCacheEnvConfig;
import java.io.IOException;
import net.spy.memcached.AddrUtil;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.MemcachedClient;

public final class MemcachedCacheClientFactory {

    private MemcachedCacheClientFactory() {}

    public static MemcachedClient fromEnvironment() throws IOException {
        return from(MemcachedCacheEnvConfig.fromEnvironment());
    }

    public static MemcachedClient from(MemcachedCacheEnvConfig config) throws IOException {
        var addresses = AddrUtil.getAddresses(config.nodes());
        var factory = new ConnectionFactoryBuilder()
                .setOpTimeout(config.operationTimeoutMillis())
                .build();

        return new MemcachedClient(factory, addresses);
    }
}
