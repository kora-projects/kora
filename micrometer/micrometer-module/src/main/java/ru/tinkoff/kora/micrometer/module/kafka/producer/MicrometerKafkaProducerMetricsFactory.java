package ru.tinkoff.kora.micrometer.module.kafka.producer;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetricsFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;

public class MicrometerKafkaProducerMetricsFactory implements KafkaProducerMetricsFactory {
    private final MeterRegistry meterRegistry;

    public MicrometerKafkaProducerMetricsFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Nullable
    @Override
    public KafkaProducerMetrics get(TelemetryConfig.MetricsConfig metrics, Producer<?, ?> producer, Properties properties) {
        if (Objects.requireNonNullElse(metrics.enabled(), true)) {
            return new MicrometerKafkaProducerMetrics(meterRegistry, producer);
        } else {
            return null;
        }
    }
}
