package io.koraframework.kafka.avro.telemetry.impl;

import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetry;
import io.koraframework.kafka.avro.telemetry.AvroRegistryTelemetryFactory;

public final class NoopAvroRegistryTelemetryFactory implements AvroRegistryTelemetryFactory {

    public static final NoopAvroRegistryTelemetryFactory INSTANCE = new NoopAvroRegistryTelemetryFactory();

    private NoopAvroRegistryTelemetryFactory() {}

    @Override
    public AvroRegistryTelemetry get() {
        return NoopAvroRegistryTelemetry.INSTANCE;
    }
}
