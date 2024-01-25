package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public final class DefaultKafkaConsumerTelemetryFactory<K, V> implements KafkaConsumerTelemetryFactory<K, V> {
    @Nullable
    private final KafkaConsumerLoggerFactory<K, V> logger;
    @Nullable
    private final KafkaConsumerMetricsFactory metrics;
    @Nullable
    private final KafkaConsumerTracerFactory tracer;

    public DefaultKafkaConsumerTelemetryFactory(@Nullable KafkaConsumerLoggerFactory<K, V> logger, @Nullable KafkaConsumerMetricsFactory metrics, @Nullable KafkaConsumerTracerFactory tracer) {
        this.logger = logger;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @Override
    public KafkaConsumerTelemetry<K, V> get(Properties driverProperties, TelemetryConfig config) {
        var logger = this.logger == null ? null : this.logger.get(driverProperties, config.logging());
        var metrics = this.metrics == null ? null : this.metrics.get(driverProperties, config.metrics());
        var tracer = this.tracer == null ? null : this.tracer.get(driverProperties, config.tracing());

        if (logger == null && metrics == null && tracer == null) {
            return KafkaConsumerTelemetryFactory.empty();
        }

        return new DefaultKafkaConsumerTelemetry<>(logger, tracer, metrics);
    }
}
