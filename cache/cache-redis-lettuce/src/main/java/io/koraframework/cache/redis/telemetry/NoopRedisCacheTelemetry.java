package io.koraframework.cache.redis.telemetry;

public final class NoopRedisCacheTelemetry implements RedisCacheTelemetry {

    public static final NoopRedisCacheTelemetry INSTANCE = new NoopRedisCacheTelemetry();

    private NoopRedisCacheTelemetry() {}

    @Override
    public RedisCacheObservation observe(String operation) {
        return NoopRedisCacheObservation.INSTANCE;
    }
}
