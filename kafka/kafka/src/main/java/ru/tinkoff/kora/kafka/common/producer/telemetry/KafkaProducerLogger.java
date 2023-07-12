package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;

import javax.annotation.Nullable;
import java.util.Map;

public interface KafkaProducerLogger {
    void sendBegin(ProducerRecord<?, ?> record);

    void sendEnd(ProducerRecord<?, ?> record, Throwable e);

    void sendEnd(RecordMetadata metadata);

    void txBegin();

    void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata);

    void txCommit();

    void txRollback(@Nullable Throwable e);
}
