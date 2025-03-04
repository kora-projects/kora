package ru.tinkoff.kora.cache.redis.jedis;

import redis.clients.jedis.UnifiedJedis;
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.cache.redis.RedisCacheModule;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.redis.jedis.JedisModule;

public interface JedisCacheModule extends RedisCacheModule, JedisModule {

    @DefaultComponent
    default RedisCacheClient lettuceRedisClient(UnifiedJedis jedis) {
        return new JedisCacheSyncClient(jedis);
    }

    @DefaultComponent
    default RedisCacheAsyncClient lettuceRedisAsyncClient(RedisCacheClient redisCacheClient) {
        return new JedisCacheAsyncClient(redisCacheClient);
    }
}
