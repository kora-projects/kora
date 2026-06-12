package io.koraframework.kafka.common.consumer.telemetry.impl;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.jspecify.annotations.Nullable;
import org.slf4j.helpers.NOPLogger;

public final class NoopKafkaConsumerLoggerFactory extends DefaultKafkaConsumerLoggerFactory {

    public static final NoopKafkaConsumerLoggerFactory INSTANCE = new NoopKafkaConsumerLoggerFactory();

    private NoopKafkaConsumerLoggerFactory() {}

    @Override
    public DefaultKafkaConsumerLogger create(DefaultKafkaConsumerTelemetry.TelemetryContext context) {
        return NoopKafkaConsumerLogger.INSTANCE;
    }

    public static final class NoopKafkaConsumerLogger extends DefaultKafkaConsumerLogger {

        public static final NoopKafkaConsumerLogger INSTANCE = new NoopKafkaConsumerLogger();

        private NoopKafkaConsumerLogger() {
            super(NOPLogger.NOP_LOGGER, DefaultKafkaConsumerTelemetry.TelemetryContext.EMPTY);
        }

        @Override
        public void logPollStart() {

        }

        @Override
        public void logRecordsReceived(ConsumerRecords<?, ?> records) {

        }

        @Override
        public void logPollEnd(@Nullable ConsumerRecords<?, ?> records, @Nullable Throwable error) {

        }

        @Override
        public void logRecordStart(ConsumerRecord<?, ?> record) {

        }

        @Override
        public void logRecordEnd(ConsumerRecord<?, ?> record, @Nullable Throwable error) {

        }
    }
}
