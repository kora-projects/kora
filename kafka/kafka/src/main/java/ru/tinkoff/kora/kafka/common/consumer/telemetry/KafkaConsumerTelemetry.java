package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;

public interface KafkaConsumerTelemetry<K, V> {

    interface KafkaConsumerTelemetryContext<K, V> extends AutoCloseable {
        @Override
        void close();
    }

    default KafkaConsumerTelemetryContext<K, V> get(Consumer<K, V> consumer) {
        return () -> {};
    }

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
