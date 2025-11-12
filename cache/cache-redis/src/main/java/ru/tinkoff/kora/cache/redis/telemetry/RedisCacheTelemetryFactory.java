package ru.tinkoff.kora.cache.redis.telemetry;

import ru.tinkoff.kora.cache.redis.RedisCacheClientConfig;
import ru.tinkoff.kora.cache.redis.RedisCacheConfig;

public interface RedisCacheTelemetryFactory {
    RedisCacheTelemetry get(RedisCacheClientConfig clientConfig, RedisCacheConfig cacheConfig, String cacheName);
}
