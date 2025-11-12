package ru.tinkoff.kora.cache.redis.testdata;

import ru.tinkoff.kora.cache.redis.*;
import ru.tinkoff.kora.cache.redis.telemetry.RedisCacheTelemetryFactory;

public final class DummyCache extends AbstractRedisCache<String, String> {

    public DummyCache(RedisCacheConfig config,
                      RedisCacheClient redisClient,
                      RedisCacheTelemetryFactory telemetry,
                      RedisCacheKeyMapper<String> keyMapper,
                      RedisCacheValueMapper<String> valueMapper) {
        super("dummy", config, redisClient, telemetry, keyMapper, valueMapper);
    }
}
