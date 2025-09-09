package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

public interface KafkaConsumerMetrics {

    void onRecordsReceived(ConsumerRecords<?, ?> records);

    void onRecordsProcessed(String consumerName, ConsumerRecords<?, ?> records, long duration, @Nullable Throwable ex);

    void onRecordProcessed(String consumerName, ConsumerRecord<?, ?> record, long duration, @Nullable Throwable ex);

    void reportLag(String consumerName, TopicPartition partition, long lag);

    interface KafkaConsumerMetricsContext extends AutoCloseable {
        @Override
        void close();
    }

    default KafkaConsumerMetricsContext get(Consumer<?, ?> consumer) {
        return () -> {};
    }
}
