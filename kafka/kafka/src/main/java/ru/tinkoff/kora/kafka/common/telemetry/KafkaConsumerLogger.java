package ru.tinkoff.kora.kafka.common.telemetry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import javax.annotation.Nullable;

public interface KafkaConsumerLogger<K, V> {
    void logRecords(ConsumerRecords<K, V> records);

    void logRecord(ConsumerRecord<K, V> record);

    void logRecordProcessed(ConsumerRecord<K, V> record, @Nullable Throwable ex);

    void logRecordsProcessed(ConsumerRecords<K, V> records, @Nullable Throwable ex);
}
