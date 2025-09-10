package ru.tinkoff.kora.kafka.common.producer.telemetry;

import jakarta.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import ru.tinkoff.kora.kafka.common.producer.telemetry.KafkaProducerTelemetry.TelemetryProducerRecord;

import java.util.Map;

public interface KafkaProducerLogger {
    void sendBegin(TelemetryProducerRecord<?, ?> record);

    void sendEnd(TelemetryProducerRecord<?, ?> record, Throwable e);

    void sendEnd(TelemetryProducerRecord<?, ?> record, RecordMetadata metadata);

    void txBegin();

    void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata);

    void txCommit();

    void txRollback(@Nullable Throwable e);
}
