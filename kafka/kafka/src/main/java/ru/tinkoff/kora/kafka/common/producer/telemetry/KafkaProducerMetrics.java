package ru.tinkoff.kora.kafka.common.producer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public interface KafkaProducerMetrics {
    KafkaProducerTxMetrics tx();

    void sendEnd(ProducerRecord<?, ?> record, long durationNanos, Throwable e);

    void sendEnd(ProducerRecord<?, ?> record, long durationNanos, RecordMetadata metadata);

    interface KafkaProducerTxMetrics {
        void commit();

        void rollback(@Nullable Throwable e);
    }

}
