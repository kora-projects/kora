package io.koraframework.kafka.common.producer.telemetry.impl;

import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

import java.util.Map;

public final class NoopKafkaPublisherLoggerFactory extends DefaultKafkaPublisherLoggerFactory {

    public static final NoopKafkaPublisherLoggerFactory INSTANCE = new NoopKafkaPublisherLoggerFactory();

    private NoopKafkaPublisherLoggerFactory() {}

    @Override
    public DefaultKafkaPublisherLogger create(DefaultKafkaPublisherTelemetry.TelemetryContext context) {
        return NoopKafkaPublisherLogger.INSTANCE;
    }

    public static final class NoopKafkaPublisherLogger extends DefaultKafkaPublisherLogger {

        public static final NoopKafkaPublisherLogger INSTANCE = new NoopKafkaPublisherLogger();

        private NoopKafkaPublisherLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultKafkaPublisherTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logRecordStart(ProducerRecord<byte[], byte[]> record) {

        }

        @Override
        public void logRecordEnd(String topic, @Nullable RecordMetadata metadata, @Nullable Throwable error) {

        }

        @Override
        public void logTxOffsets(Map<TopicPartition, OffsetAndMetadata> offsets) {

        }

        @Override
        public void logTxCommitStart() {

        }

        @Override
        public void logTxRollbackStart(@Nullable Throwable error) {

        }

        @Override
        public void logTxEnd(@Nullable Throwable error) {

        }
    }
}
