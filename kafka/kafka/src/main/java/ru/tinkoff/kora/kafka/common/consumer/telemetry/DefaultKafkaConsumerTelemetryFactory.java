package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Properties;

public final class DefaultKafkaConsumerTelemetryFactory<K, V> implements KafkaConsumerTelemetryFactory<K, V> {

    private final KafkaConsumerTelemetry<K, V> empty;

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

        KafkaConsumerTelemetry.KafkaConsumerRecordTelemetryContext<K, V> emptyRcdContext = ex -> {};
        KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext<K, V> emptyCtx = new KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext<K, V>() {
            @Override
            public KafkaConsumerTelemetry.KafkaConsumerRecordTelemetryContext<K, V> get(ConsumerRecord<K, V> record) {
                return emptyRcdContext;
            }

            @Override
            public void close(@Nullable Throwable ex) {

            }
        };

        this.empty = new KafkaConsumerTelemetry<>() {
            @Override
            public KafkaConsumerRecordsTelemetryContext<K, V> get(ConsumerRecords<K, V> records) {
                return emptyCtx;
            }

            @Override
            public void reportLag(TopicPartition partition, long lag) {

            }
        };
    }

    @Deprecated
    @Override
    public KafkaConsumerTelemetry<K, V> get(Properties driverProperties, TelemetryConfig config) {
        var logger = this.logger == null ? null : this.logger.get(driverProperties, config.logging());
        var metrics = this.metrics == null ? null : this.metrics.get(driverProperties, config.metrics());
        var tracer = this.tracer == null ? null : this.tracer.get(driverProperties, config.tracing());
        if (logger == null && metrics == null && tracer == null) {
            return empty;
        }

        return new DefaultKafkaConsumerTelemetry<>("", logger, tracer, metrics);
    }

    @Override
    public KafkaConsumerTelemetry<K, V> get(String consumerName, Properties driverProperties, TelemetryConfig config) {
        var logger = this.logger == null ? null : this.logger.get(driverProperties, config.logging());
        var metrics = this.metrics == null ? null : this.metrics.get(driverProperties, config.metrics());
        var tracer = this.tracer == null ? null : this.tracer.get(driverProperties, config.tracing());
        if (logger == null && metrics == null && tracer == null) {
            return empty;
        }

        return new DefaultKafkaConsumerTelemetry<>(consumerName, logger, tracer, metrics);
    }
}
