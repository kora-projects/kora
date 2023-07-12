package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerMetrics.KafkaProducerTxMetrics;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTracer.KafkaProducerRecordSpan;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTracer.KafkaProducerTxSpan;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Properties;

public class DefaultKafkaProducerTelemetryFactory implements KafkaProducerTelemetryFactory {
    @Nullable
    private final KafkaProducerTracerFactory tracerFactory;
    @Nullable
    private final KafkaProducerLoggerFactory loggerFactory;
    @Nullable
    private final KafkaProducerMetricsFactory metricsFactory;

    public DefaultKafkaProducerTelemetryFactory(@Nullable KafkaProducerTracerFactory tracerFactory, @Nullable KafkaProducerLoggerFactory loggerFactory, @Nullable KafkaProducerMetricsFactory metricsFactory) {
        this.tracerFactory = tracerFactory;
        this.loggerFactory = loggerFactory;
        this.metricsFactory = metricsFactory;
    }

    @Override
    public KafkaProducerTelemetry get(Producer<?, ?> producer, Properties properties) {
        var tracer = this.tracerFactory == null ? null : this.tracerFactory.get(producer, properties);
        var logger = this.loggerFactory == null ? null : this.loggerFactory.get(producer, properties);
        var metrics = this.metricsFactory == null ? null : this.metricsFactory.get(producer, properties);

        return new DefaultKafkaProducerTelemetry(tracer, logger, metrics);
    }

    private static final class DefaultKafkaProducerTelemetry implements KafkaProducerTelemetry {
        @Nullable
        private final KafkaProducerTracer tracer;
        @Nullable
        private final KafkaProducerLogger logger;
        @Nullable
        private final KafkaProducerMetrics metrics;

        public DefaultKafkaProducerTelemetry(@Nullable KafkaProducerTracer tracer, @Nullable KafkaProducerLogger logger, @Nullable KafkaProducerMetrics metrics) {
            this.tracer = tracer;
            this.logger = logger;
            this.metrics = metrics;
        }

        @Override
        public void close() {
            if (this.tracer instanceof AutoCloseable c) {
                try {
                    c.close();
                } catch (Exception ignore) {}
            }
            if (this.logger instanceof AutoCloseable c) {
                try {
                    c.close();
                } catch (Exception ignore) {}
            }
            if (this.metrics instanceof AutoCloseable c) {
                try {
                    c.close();
                } catch (Exception ignore) {}
            }
        }

        @Override
        public KafkaProducerTransactionTelemetryContext tx() {
            var span = this.tracer == null ? null : this.tracer.tx();
            var metrics = this.metrics == null ? null : this.metrics.tx();
            if (this.logger != null) {
                this.logger.txBegin();
            }
            return new DefaultKafkaProducerTransactionTelemetryContext(span, this.logger, metrics);
        }

        @Override
        public KafkaProducerRecordTelemetryContext record(ProducerRecord<?, ?> record) {
            if (this.logger != null) {
                this.logger.sendBegin(record);
            }
            var span = this.tracer == null ? null : this.tracer.get(record);

            return new DefaultKafkaProducerRecordTelemetryContext(record, span, this.logger, this.metrics);
        }
    }

    private static final class DefaultKafkaProducerTransactionTelemetryContext implements KafkaProducerTelemetry.KafkaProducerTransactionTelemetryContext {
        @Nullable
        private final KafkaProducerTxSpan span;
        @Nullable
        private final KafkaProducerLogger logger;
        @Nullable
        private final KafkaProducerTxMetrics metrics;

        private DefaultKafkaProducerTransactionTelemetryContext(@Nullable KafkaProducerTxSpan span, @Nullable KafkaProducerLogger logger, @Nullable KafkaProducerTxMetrics metrics) {
            this.span = span;
            this.logger = logger;
            this.metrics = metrics;
        }

        @Override
        public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) {
            if (this.logger != null) {
                this.logger.sendOffsetsToTransaction(offsets, groupMetadata);
            }
        }

        @Override
        public void commit() {
            if (this.metrics != null) {
                this.metrics.commit();
            }
            if (this.logger != null) {
                this.logger.txCommit();
            }
            if (this.span != null) {
                this.span.commit();
            }
        }

        @Override
        public void rollback(@Nullable Throwable e) {
            if (this.metrics != null) {
                this.metrics.rollback(e);
            }
            if (this.logger != null) {
                this.logger.txRollback(e);
            }
            if (this.span != null) {
                this.span.rollback(e);
            }
        }
    }

    private static final class DefaultKafkaProducerRecordTelemetryContext implements KafkaProducerTelemetry.KafkaProducerRecordTelemetryContext {
        private final KafkaProducerRecordSpan span;
        private final KafkaProducerLogger logger;
        private final ProducerRecord<?, ?> record;
        private final Context ctx;

        public DefaultKafkaProducerRecordTelemetryContext(ProducerRecord<?, ?> record, @Nullable KafkaProducerRecordSpan span, @Nullable KafkaProducerLogger logger, @Nullable KafkaProducerMetrics metrics) {
            this.span = span;
            this.logger = logger;
            this.record = record;
            this.ctx = Context.current().fork();
        }

        @Override
        public void sendEnd(Throwable e) {
            var oldCtx = Context.current();
            try {
                this.ctx.inject();
                if (this.logger != null) {
                    this.logger.sendEnd(record, e);
                }
                if (this.span != null) {
                    this.span.close(e);
                }
            } finally {
                oldCtx.inject();
            }
        }

        @Override
        public void sendEnd(RecordMetadata metadata) {
            var oldCtx = Context.current();
            try {
                this.ctx.inject();
                if (this.logger != null) {
                    this.logger.sendEnd(metadata);
                }
                if (this.span != null) {
                    this.span.close(metadata);
                }
            } finally {
                oldCtx.inject();
            }
        }
    }
}
