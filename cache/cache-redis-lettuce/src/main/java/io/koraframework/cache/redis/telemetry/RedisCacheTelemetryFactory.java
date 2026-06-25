package io.koraframework.cache.redis.telemetry;

public interface RedisCacheTelemetryFactory {

    RedisCacheTelemetry get(String cacheConfigPath, Class<?> cacheImpl, RedisCacheTelemetryConfig config);
}
