package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

public interface KafkaConsumerMetrics {

    void onRecordsReceived(ConsumerRecords<?, ?> records);

    /**
     * @see #onRecordsProcessed(String, ConsumerRecords, long, Throwable)
     */
    @Deprecated
    default void onRecordsProcessed(ConsumerRecords<?, ?> records, long duration, @Nullable Throwable ex) {

    }

    default void onRecordsProcessed(String consumerName, ConsumerRecords<?, ?> records, long duration, @Nullable Throwable ex) {
        onRecordsProcessed(records, duration, ex);
    }

    void onRecordProcessed(ConsumerRecord<?, ?> record, long duration, @Nullable Throwable ex);

    @Deprecated
    default void reportLag(TopicPartition partition, long lag) {

    }

    default void reportLag(String consumerName, TopicPartition partition, long lag) {
        reportLag(partition, lag);
    }

    interface KafkaConsumerMetricsContext extends AutoCloseable {
        @Override
        void close();
    }

    default KafkaConsumerMetricsContext get(Consumer<?, ?> consumer) {
        return () -> { };
    }
}
