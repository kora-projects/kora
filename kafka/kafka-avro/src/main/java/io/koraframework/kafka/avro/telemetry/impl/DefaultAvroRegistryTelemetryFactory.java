package io.koraframework.kafka.avro.telemetry.impl;

import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetry;
import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;

/**
 * <b>Русский</b>: Фабрика телеметрии Avro: возвращает реализацию на Micrometer, если доступен
 * {@link MeterRegistry}, иначе — пустую реализацию.
 * <hr>
 * <b>English</b>: Avro telemetry factory: returns the Micrometer-backed implementation when a
 * {@link MeterRegistry} is available, otherwise a no-op implementation.
 */
public final class DefaultAvroRegistryTelemetryFactory implements AvroRegistryTelemetryFactory {

    @Nullable
    private final MeterRegistry meterRegistry;

    public DefaultAvroRegistryTelemetryFactory(@Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public AvroRegistryTelemetry get() {
        return this.meterRegistry == null
            ? NoopAvroRegistryTelemetry.INSTANCE
            : new DefaultAvroRegistryTelemetry(this.meterRegistry);
    }
}
