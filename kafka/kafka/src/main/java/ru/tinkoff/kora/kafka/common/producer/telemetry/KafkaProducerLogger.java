package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTelemetry.TelemetryProducerRecord;

import jakarta.annotation.Nullable;

import java.util.Map;

public interface KafkaProducerLogger {
    @Deprecated
    void sendBegin(ProducerRecord<?, ?> record);

    default void sendBegin(TelemetryProducerRecord<?, ?> record) {
        sendBegin(record.producerRecord());
    }

    @Deprecated
    void sendEnd(ProducerRecord<?, ?> record, Throwable e);

    default void sendEnd(TelemetryProducerRecord<?, ?> record, Throwable e) {
        sendEnd(record.producerRecord(), e);
    }

    @Deprecated
    void sendEnd(RecordMetadata metadata);

    default void sendEnd(TelemetryProducerRecord<?, ?> record, RecordMetadata metadata) {
        sendEnd(metadata);
    }

    void txBegin();

    void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata);

    void txCommit();

    void txRollback(@Nullable Throwable e);
}
