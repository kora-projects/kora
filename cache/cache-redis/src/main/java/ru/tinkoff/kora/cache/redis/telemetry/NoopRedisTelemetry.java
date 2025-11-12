package ru.tinkoff.kora.cache.redis.telemetry;

public class NoopRedisTelemetry implements RedisCacheTelemetry {
    public static final NoopRedisTelemetry INSTANCE = new NoopRedisTelemetry();

    @Override
    public RedisCacheObservation observe(String operation) {
        return NoopRedisObservation.INSTANCE;
    }
}
