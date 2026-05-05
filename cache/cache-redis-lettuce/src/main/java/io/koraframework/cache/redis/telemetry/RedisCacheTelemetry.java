package io.koraframework.cache.redis.telemetry;

public interface RedisCacheTelemetry {

    RedisCacheObservation observe(String operation);
}
