package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface KafkaConsumerTracerFactory {
    @Nullable
    KafkaConsumerTracer get(TelemetryConfig.TracingConfig tracing);
}
