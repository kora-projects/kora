package ru.tinkoff.kora.micrometer.module.kafka.producer;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;

public class MicrometerKafkaProducerMetricsFactory implements KafkaProducerMetricsFactory {
    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;

    public MicrometerKafkaProducerMetricsFactory(MeterRegistry meterRegistry, MetricsConfig metricsConfig) {
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
    }

    @Nullable
    @Override
    public KafkaProducerMetrics get(TelemetryConfig.MetricsConfig metrics, Producer<?, ?> producer, Properties properties) {
        if (Objects.requireNonNullElse(metrics.enabled(), true)) {
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Opentelemetry120KafkaProducerMetrics(meterRegistry, metrics, producer, properties);
                case V123 -> new Opentelemetry123KafkaProducerMetrics(meterRegistry, metrics, producer, properties);
            };
        } else {
            return null;
        }
    }
}
