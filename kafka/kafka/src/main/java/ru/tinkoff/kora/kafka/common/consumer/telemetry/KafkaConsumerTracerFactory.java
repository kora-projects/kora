package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public interface KafkaConsumerTracerFactory {
    @Nullable
    KafkaConsumerTracer get(Properties driverProperties, TelemetryConfig.TracingConfig tracing);
}
