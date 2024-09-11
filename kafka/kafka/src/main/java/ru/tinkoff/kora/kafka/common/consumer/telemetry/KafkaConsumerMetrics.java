package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

public interface KafkaConsumerMetrics {

    void onRecordsReceived(ConsumerRecords<?, ?> records);

    void onRecordsProcessed(ConsumerRecords<?, ?> records, long duration, @Nullable Throwable ex);

    void onRecordProcessed(ConsumerRecord<?, ?> record, long duration, @Nullable Throwable ex);

    void reportLag(TopicPartition partition, long lag);
}
