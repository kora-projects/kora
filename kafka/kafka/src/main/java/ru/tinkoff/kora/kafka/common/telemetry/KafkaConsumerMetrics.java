package ru.tinkoff.kora.kafka.common.telemetry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public interface KafkaConsumerMetrics {
    void onRecordsReceived(ConsumerRecords<?, ?> records);

    void onRecordsProcessed(ConsumerRecords<?, ?> records, long duration, Throwable ex);

    void onRecordProcessed(ConsumerRecord<?, ?> record, long duration, Throwable ex);
}
