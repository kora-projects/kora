package ru.tinkoff.kora.micrometer.module.kafka.consumer;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerMetrics;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerMetricsFactory;
import ru.tinkoff.kora.micrometer.module.MetricsConfig;
import ru.tinkoff.kora.micrometer.module.kafka.consumer.tag.MicrometerKafkaConsumerTagsProvider;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;
import java.util.Properties;

public final class MicrometerKafkaConsumerMetricsFactory implements KafkaConsumerMetricsFactory {

    private final MicrometerKafkaConsumerTagsProvider consumerTagsProvider;
    private final MeterRegistry meterRegistry;
    private final MetricsConfig metricsConfig;

    public MicrometerKafkaConsumerMetricsFactory(MicrometerKafkaConsumerTagsProvider consumerTagsProvider,
                                                 MeterRegistry meterRegistry,
                                                 MetricsConfig metricsConfig) {
        this.consumerTagsProvider = consumerTagsProvider;
        this.meterRegistry = meterRegistry;
        this.metricsConfig = metricsConfig;
    }

    @Nullable
    @Override
    public KafkaConsumerMetrics get(Properties driverProperties, TelemetryConfig.MetricsConfig metrics) {
        if (Objects.requireNonNullElse(metrics.enabled(), true)) {
            return switch (metricsConfig.opentelemetrySpec()) {
                case V120 -> new Opentelemetry120KafkaConsumerMetrics(this.consumerTagsProvider, this.meterRegistry, driverProperties, metrics);
                case V123 -> new Opentelemetry123KafkaConsumerMetrics(this.consumerTagsProvider, this.meterRegistry, driverProperties, metrics);
            };
        }
        return null;
    }
}
