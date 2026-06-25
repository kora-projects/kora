package io.koraframework.cache.redis.testdata;

import io.koraframework.cache.redis.*;
import io.koraframework.cache.redis.telemetry.RedisCacheTelemetryFactory;

public final class DummyCache extends AbstractRedisCache<String, String> {

    public DummyCache(RedisCacheConfig config,
                      RedisCacheClient redisClient,
                      RedisCacheTelemetryFactory telemetry,
                      RedisCacheKeyMapper<String> keyMapper,
                      RedisCacheValueMapper<String> valueMapper) {
        super("dummy", config, redisClient, telemetry, keyMapper, valueMapper);
    }
}
