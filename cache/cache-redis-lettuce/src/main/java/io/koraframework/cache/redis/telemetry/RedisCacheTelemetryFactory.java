package io.koraframework.cache.redis.telemetry;

public interface RedisCacheTelemetryFactory {

    RedisCacheTelemetry get(String cacheName, String cacheImpl, RedisCacheTelemetryConfig config);
}
