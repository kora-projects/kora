package io.koraframework.cache.redis.telemetry.impl;

import io.koraframework.cache.redis.telemetry.RedisCacheObservation;
import io.koraframework.cache.redis.telemetry.RedisCacheTelemetry;

public final class NoopRedisCacheTelemetry implements RedisCacheTelemetry {

    public static final NoopRedisCacheTelemetry INSTANCE = new NoopRedisCacheTelemetry();

    private NoopRedisCacheTelemetry() {}

    @Override
    public RedisCacheObservation observe(String operation) {
        return NoopRedisCacheObservation.INSTANCE;
    }
}
