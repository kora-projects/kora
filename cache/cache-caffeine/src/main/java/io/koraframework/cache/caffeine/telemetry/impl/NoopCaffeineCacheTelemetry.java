package io.koraframework.cache.caffeine.telemetry.impl;

import io.koraframework.cache.caffeine.telemetry.CaffeineCacheObservation;
import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetry;

public final class NoopCaffeineCacheTelemetry implements CaffeineCacheTelemetry {

    public static final NoopCaffeineCacheTelemetry INSTANCE = new NoopCaffeineCacheTelemetry();

    private NoopCaffeineCacheTelemetry() {}

    @Override
    public CaffeineCacheObservation observe(Operation operation) {
        return NoopCaffeineCacheObservation.INSTANCE;
    }
}
