package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;

import java.util.Map;

public interface KafkaConsumerTelemetry<K, V> {

    interface KafkaConsumerRecordsTelemetryContext<K, V> {

        KafkaConsumerRecordTelemetryContext<K, V> get(ConsumerRecord<K, V> record);

        void close(@Nullable Throwable ex);
    }

    interface KafkaConsumerRecordTelemetryContext<K, V> {
        void close(@Nullable Throwable ex);
    }

    KafkaConsumerRecordsTelemetryContext<K, V> get(ConsumerRecords<K, V> records);

    void reportLag(TopicPartition partition, long lag);
}
