package ru.tinkoff.kora.cache.redis.jedis.testdata;

import ru.tinkoff.kora.cache.redis.*;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetryFactory;

public final class DummyCache extends AbstractRedisCache<String, String> {

    public DummyCache(RedisCacheConfig config,
                      RedisCacheClient redisClient,
                      RedisCacheAsyncClient redisAsyncClient,
                      CacheTelemetryFactory telemetryFactory,
                      RedisCacheKeyMapper<String> keyMapper,
                      RedisCacheValueMapper<String> valueMapper) {
        super("dummy", config, redisClient, redisAsyncClient, telemetryFactory, keyMapper, valueMapper);
    }
}
