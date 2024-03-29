package ru.tinkoff.kora.micrometer.module.kafka.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerMetrics;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;

public final class MicrometerKafkaConsumerMetricsFactory implements KafkaConsumerMetricsFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerKafkaConsumerMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Nullable
    @Override
    public KafkaConsumerMetrics get(Properties driverProperties, TelemetryConfig.MetricsConfig metrics) {
        if (Objects.requireNonNullElse(metrics.enabled(), true)) {
            return new MicrometerKafkaConsumerMetrics(this.meterRegistry, driverProperties, metrics);
        }
        return null;
    }
}
