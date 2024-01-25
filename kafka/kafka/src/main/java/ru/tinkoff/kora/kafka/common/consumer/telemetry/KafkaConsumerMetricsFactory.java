package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public interface KafkaConsumerMetricsFactory {
    @Nullable
    KafkaConsumerMetrics get(Properties driverProperties, TelemetryConfig.MetricsConfig metrics);
}
