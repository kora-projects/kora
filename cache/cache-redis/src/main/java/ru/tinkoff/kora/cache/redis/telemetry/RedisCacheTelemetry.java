package ru.tinkoff.kora.cache.redis.telemetry;

public interface RedisCacheTelemetry {

    RedisCacheObservation observe(String operation);
}
