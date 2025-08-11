package ru.tinkoff.kora.micrometer.module.kafka.producer;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.Producer;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.tag.MicrometerKafkaConsumerTagsProvider;
import ru.tinkoff.kora.micrometer.module.kafka.producer.tag.MicrometerKafkaProducerTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;

public class MicrometerKafkaProducerMetricsFactory implements KafkaProducerMetricsFactory {

    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;
    private final MicrometerKafkaProducerTagsProvider tagsProvider;

    public MicrometerKafkaProducerMetricsFactory(MeterRegistry meterRegistry,
                                                 MetricsConfig metricsConfig,
                                                 MicrometerKafkaProducerTagsProvider tagsProvider) {
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
        this.tagsProvider = tagsProvider;
    }

    @Nullable
    @Override
    public KafkaProducerMetrics get(TelemetryConfig.MetricsConfig metrics, Producer<?, ?> producer, Properties properties) {
        if (Objects.requireNonNullElse(metrics.enabled(), true)) {
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Opentelemetry120KafkaProducerMetrics(this.meterRegistry, metrics, producer, properties, this.tagsProvider);
                case V123 -> new Opentelemetry123KafkaProducerMetrics(this.meterRegistry, metrics, producer, properties, this.tagsProvider);
            };
        } else {
            return null;
        }
    }
}
