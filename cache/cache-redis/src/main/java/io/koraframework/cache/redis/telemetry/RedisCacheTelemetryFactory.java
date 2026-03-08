package io.koraframework.cache.redis.telemetry;

import io.koraframework.cache.redis.RedisCacheClientConfig;
import io.koraframework.cache.redis.RedisCacheConfig;

public interface RedisCacheTelemetryFactory {
    RedisCacheTelemetry get(RedisCacheClientConfig clientConfig, RedisCacheConfig cacheConfig, String cacheName);
}
