package ru.tinkoff.kora.opentelemetry.module.kafka.consumer;

import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTracer;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;

public final class OpentelemetryKafkaConsumerTracerFactory implements KafkaConsumerTracerFactory {
    private final Tracer tracer;

    public OpentelemetryKafkaConsumerTracerFactory(Tracer tracer) {
        this.tracer = tracer;
    }

    @Nullable
    @Override
    public KafkaConsumerTracer get(Properties driverProperties, TelemetryConfig.TracingConfig tracing) {
        if (Objects.requireNonNullElse(tracing.enabled(), true)) {
            return new OpentelemetryKafkaConsumerTracer(this.tracer);
        }
        return null;
    }
}
