package ru.tinkoff.kora.kafka.common.producer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;

import java.util.Map;

public interface KafkaProducerTelemetry extends AutoCloseable {
    @Override
    void close();

    KafkaProducerTransactionTelemetryContext tx();

    KafkaProducerRecordTelemetryContext record(TelemetryProducerRecord<?, ?> record);

    interface KafkaProducerTransactionTelemetryContext {
        void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata);

        void commit();

        void rollback(@Nullable Throwable e);
    }

    record TelemetryProducerRecord<K, V>(K key, V value, ProducerRecord<?, ?> producerRecord) {};

    interface KafkaProducerRecordTelemetryContext extends Callback {
        void sendEnd(Throwable e);

        void sendEnd(RecordMetadata metadata);

        @Override
        default void onCompletion(@Nullable RecordMetadata metadata, @Nullable Exception exception) {
            if (metadata != null) {
                this.sendEnd(metadata);
            }
            if (exception != null) {
                this.sendEnd(exception);
            }
        }
    }

}
