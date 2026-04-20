package io.koraframework.kafka.common.consumer.telemetry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;

public final class NoopKafkaConsumerMetricsFactory extends DefaultKafkaConsumerMetricsFactory {

    public static final NoopKafkaConsumerMetricsFactory INSTANCE = new NoopKafkaConsumerMetricsFactory();

    private NoopKafkaConsumerMetricsFactory() {}

    private static final DefaultKafkaConsumerMetricsFactory.DefaultKafkaConsumerMetrics NOOP_METRICS = new DefaultKafkaConsumerMetricsFactory.DefaultKafkaConsumerMetrics(null) {

        @Override
        public void reportHandleRecordTook(ConsumerRecord<?, ?> record, long startedRecordHandleInNanos, @Nullable Throwable error) {
            // do nothing
        }

        @Override
        public void reportHandleRecordsBatchTook(ConsumerRecords<?, ?> records, long startedRecordsHandleInNanos, @Nullable Throwable error) {
            // do nothing
        }

        @Override
        public void reportTopicLag(TopicPartition partition, long lag) {
            // do nothing
        }
    };

    @Override
    public DefaultKafkaConsumerMetrics create(DefaultKafkaConsumerTelemetry.TelemetryContext context) {
        return NOOP_METRICS;
    }
}
