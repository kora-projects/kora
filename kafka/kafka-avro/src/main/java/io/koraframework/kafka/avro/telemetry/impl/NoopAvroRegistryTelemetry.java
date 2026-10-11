package io.koraframework.kafka.avro.telemetry.impl;

import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetry;
import org.jspecify.annotations.Nullable;

/**
 * <b>Русский</b>: Пустая реализация {@link AvroRegistryTelemetry} (без метрик).
 * <hr>
 * <b>English</b>: No-op implementation of {@link AvroRegistryTelemetry} (records nothing).
 */
public final class NoopAvroRegistryTelemetry implements AvroRegistryTelemetry {

    public static final NoopAvroRegistryTelemetry INSTANCE = new NoopAvroRegistryTelemetry();

    private NoopAvroRegistryTelemetry() {}

    @Override
    public void observeRegistryLookup(String operation, boolean success, long durationNanos) {

    }

    @Override
    public void observeCache(String operation, boolean hit) {

    }

    @Override
    public void observeError(String operation, @Nullable String topic) {

    }
}
