package io.koraframework.kafka.common.producer.telemetry;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.jspecify.annotations.Nullable;

public final class NoopKafkaPublisherMetricsFactory extends DefaultKafkaPublisherMetricsFactory {

    public static final NoopKafkaPublisherMetricsFactory INSTANCE = new NoopKafkaPublisherMetricsFactory();

    private NoopKafkaPublisherMetricsFactory() {}

    private static final DefaultKafkaPublisherMetrics NOOP_METRICS = new DefaultKafkaPublisherMetrics(null) {

        @Override
        public void reportHandleRecordTook(String topic, @Nullable Object recordKey, Object recordValue, @Nullable ProducerRecord<byte[], byte[]> record, @Nullable RecordMetadata metadata, @Nullable Throwable error, long startedRecordHandleInNanos) {
            // do nothing
        }
    };

    @Override
    public DefaultKafkaPublisherMetrics create(DefaultKafkaPublisherTelemetry.TelemetryContext context) {
        return NOOP_METRICS;
    }
}
