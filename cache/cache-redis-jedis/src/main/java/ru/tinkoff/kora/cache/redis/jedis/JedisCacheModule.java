package ru.tinkoff.kora.cache.redis.jedis;

import jakarta.annotation.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.UnifiedJedis;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.cache.redis.RedisCacheModule;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.redis.jedis.JedisModule;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public interface JedisCacheModule extends RedisCacheModule, JedisModule {

    @Tag(Jedis.class)
    @DefaultComponent
    default Executor jedisRedisCacheAsyncExecutor() {
        var virtualExecutor = VirtualThreadExecutorHolder.executor();
        if (virtualExecutor == null) {
            return ForkJoinPool.commonPool();
        } else {
            return virtualExecutor;
        }
    }

    default RedisCacheClient jedisRedisCacheSyncClient(UnifiedJedis jedis) {
        return new JedisCacheSyncClient(jedis);
    }

    default RedisCacheAsyncClient jedisRedisCacheAsyncClient(RedisCacheClient redisCacheClient,
                                                             @Tag(Jedis.class) @Nullable Executor executor) {
        if (executor == null) {
            return new JedisCacheStubAsyncClient(redisCacheClient);
        } else {
            return new JedisCacheAsyncClient(redisCacheClient, executor);
        }
    }
}
