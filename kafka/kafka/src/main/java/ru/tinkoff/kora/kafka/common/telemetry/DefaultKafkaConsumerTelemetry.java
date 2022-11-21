package ru.tinkoff.kora.kafka.common.telemetry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import javax.annotation.Nullable;

public class DefaultKafkaConsumerTelemetry<K, V> implements KafkaConsumerTelemetry<K, V> {
    @Nullable
    private final KafkaConsumerLogger<K, V> logger;
    @Nullable
    private final KafkaConsumerTracer tracing;
    @Nullable
    private final KafkaConsumerMetrics metrics;

    public DefaultKafkaConsumerTelemetry(@Nullable KafkaConsumerLogger<K, V> logger, @Nullable KafkaConsumerTracer tracing, @Nullable KafkaConsumerMetrics metrics) {
        this.logger = logger;
        this.tracing = tracing;
        this.metrics = metrics;
    }

    @Override
    public KafkaConsumerRecordsTelemetryContext<K, V> get(ConsumerRecords<K, V> records) {
        var start = System.nanoTime();
        if (this.metrics != null) this.metrics.onRecordsReceived(records);
        if (this.logger != null) this.logger.logRecords(records);
        var span = this.tracing == null ? null : this.tracing.get(records);

        return new DefaultKafkaConsumerRecordsTelemetryContext<>(
            records,
            this.logger,
            this.metrics,
            span,
            start
        );
    }

    private static final class DefaultKafkaConsumerRecordsTelemetryContext<K, V> implements KafkaConsumerRecordsTelemetryContext<K, V> {
        private final ConsumerRecords<K, V> records;
        @Nullable
        private final KafkaConsumerLogger<K, V> logger;
        @Nullable
        private final KafkaConsumerMetrics metrics;
        @Nullable
        private final KafkaConsumerTracer.KafkaConsumerRecordsSpan span;
        private final long start;

        public DefaultKafkaConsumerRecordsTelemetryContext(ConsumerRecords<K, V> records, @Nullable KafkaConsumerLogger<K, V> logger, @Nullable KafkaConsumerMetrics metrics, @Nullable KafkaConsumerTracer.KafkaConsumerRecordsSpan span, long start) {
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
            if (this.logger != null) this.logger.logRecord(record);
            return new DefaultKafkaConsumerRecordTelemetryContext<>(record, recordStart, this.logger, this.metrics, recordSpan);
        }

        @Override
        public void close(@Nullable Throwable ex) {
            var duration = System.nanoTime() - this.start;
            if (this.span != null) this.span.close(duration, ex);
            if (this.metrics != null) this.metrics.onRecordsProcessed(this.records, duration, ex);
            if (this.logger != null) this.logger.logRecordsProcessed(this.records, ex);
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
            if (this.recordSpan != null) this.recordSpan.close(duration, ex);
            if (this.metrics != null) this.metrics.onRecordProcessed(this.record, duration, ex);
            if (this.logger != null) this.logger.logRecordProcessed(this.record, ex);

        }
    }
}
