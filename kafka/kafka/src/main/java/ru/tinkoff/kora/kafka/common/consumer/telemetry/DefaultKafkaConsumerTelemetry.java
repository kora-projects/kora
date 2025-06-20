package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

public class DefaultKafkaConsumerTelemetry<K, V> implements KafkaConsumerTelemetry<K, V> {

    @Nullable
    private final KafkaConsumerLogger<K, V> logger;
    @Nullable
    private final KafkaConsumerTracer tracing;
    @Nullable
    private final KafkaConsumerMetrics metrics;
    private final String consumerName;

    public DefaultKafkaConsumerTelemetry(String consumerName,
                                         @Nullable KafkaConsumerLogger<K, V> logger,
                                         @Nullable KafkaConsumerTracer tracing,
                                         @Nullable KafkaConsumerMetrics metrics) {
        this.consumerName = consumerName;
        this.logger = logger;
        this.tracing = tracing;
        this.metrics = metrics;
    }

    @Override
    public KafkaConsumerRecordsTelemetryContext<K, V> get(ConsumerRecords<K, V> records) {
        var start = System.nanoTime();
        if (this.metrics != null) {
            this.metrics.onRecordsReceived(records);
        }
        if (this.logger != null) {
            this.logger.logRecords(records);
        }
        var span = this.tracing == null ? null : this.tracing.get(records);

        return new DefaultKafkaConsumerRecordsTelemetryContext<>(
            consumerName,
            records,
            this.logger,
            this.metrics,
            span,
            start
        );
    }

    @Override
    public void reportLag(TopicPartition partition, long lag) {
        if (this.metrics != null) {
            this.metrics.reportLag(consumerName, partition, lag);
        }
    }

    @Override
    public KafkaConsumerTelemetryContext<K, V> get(Consumer<K, V> consumer) {
        var metrics = this.metrics == null ? null : this.metrics.get(consumer);

        return new DefaultKafkaConsumerTelemetryContext<>(metrics);
    }

    private static final class DefaultKafkaConsumerTelemetryContext<K, V> implements KafkaConsumerTelemetryContext<K, V> {

        private final KafkaConsumerMetrics.KafkaConsumerMetricsContext metrics;

        public DefaultKafkaConsumerTelemetryContext(@Nullable KafkaConsumerMetrics.KafkaConsumerMetricsContext metrics) {
            this.metrics = metrics;
        }

        @Override
        public void close() {
            if (this.metrics != null) {
                this.metrics.close();
            }
        }
    }

    private static final class DefaultKafkaConsumerRecordsTelemetryContext<K, V> implements KafkaConsumerRecordsTelemetryContext<K, V> {
        private final ConsumerRecords<K, V> records;
        @Nullable
        private final KafkaConsumerLogger<K, V> logger;
        @Nullable
        private final KafkaConsumerMetrics metrics;
        @Nullable
        private final KafkaConsumerTracer.KafkaConsumerRecordsSpan span;
        private final String consumerName;
        private final long start;

        public DefaultKafkaConsumerRecordsTelemetryContext(String consumerName, ConsumerRecords<K, V> records, @Nullable KafkaConsumerLogger<K, V> logger, @Nullable KafkaConsumerMetrics metrics, @Nullable KafkaConsumerTracer.KafkaConsumerRecordsSpan span, long start) {
            this.consumerName = consumerName;
            this.records = records;
            this.logger = logger;
            this.metrics = metrics;
            this.span = span;
            this.start = start;
        }

        @Override
        public KafkaConsumerRecordTelemetryContext<K, V> get(ConsumerRecord<K, V> record) {
            var recordStart = System.nanoTime();
            var recordSpan = this.span == null ? null : this.span.get(record);
            if (this.logger != null) {
                this.logger.logRecord(record);
            }
            return new DefaultKafkaConsumerRecordTelemetryContext<>(record, recordStart, this.logger, this.metrics, recordSpan);
        }

        @Override
        public void close(@Nullable Throwable ex) {
            var duration = System.nanoTime() - this.start;
            if (this.span != null) {
                this.span.close(ex);
            }
            if (this.metrics != null) {
                this.metrics.onRecordsProcessed(consumerName, this.records, duration, ex);
            }
            if (this.logger != null) {
                this.logger.logRecordsProcessed(this.records, ex);
            }
        }
    }

    private static final class DefaultKafkaConsumerRecordTelemetryContext<K, V> implements KafkaConsumerRecordTelemetryContext<K, V> {
        private final ConsumerRecord<K, V> record;
        private final long recordStart;
        @Nullable
        private final KafkaConsumerLogger<K, V> logger;
        @Nullable
        private final KafkaConsumerMetrics metrics;
        @Nullable
        private final KafkaConsumerTracer.KafkaConsumerRecordSpan recordSpan;

        public DefaultKafkaConsumerRecordTelemetryContext(ConsumerRecord<K, V> record, long recordStart, @Nullable KafkaConsumerLogger<K, V> logger, @Nullable KafkaConsumerMetrics metrics, @Nullable KafkaConsumerTracer.KafkaConsumerRecordSpan recordSpan) {
            this.record = record;
            this.recordStart = recordStart;
            this.logger = logger;
            this.metrics = metrics;
            this.recordSpan = recordSpan;
        }

        @Override
        public void close(@Nullable Throwable ex) {
            var duration = System.nanoTime() - this.recordStart;
            if (this.recordSpan != null) {
                this.recordSpan.close(ex);
            }
            if (this.metrics != null) {
                this.metrics.onRecordProcessed(this.record, duration, ex);
            }
            if (this.logger != null) {
                this.logger.logRecordProcessed(this.record, ex);
            }
        }
    }
}
