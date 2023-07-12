package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Map;

public class DefaultKafkaProducerLogger implements KafkaProducerLogger {
    private static final Logger log = LoggerFactory.getLogger(DefaultKafkaProducerLogger.class);

    @Override
    public void sendBegin(ProducerRecord<?, ?> record) {

    }

    @Override
    public void sendEnd(ProducerRecord<?, ?> record, Throwable e) {
        log.warn("Error sending record to kafka topic {}", record.topic(), e);
    }

    @Override
    public void sendEnd(RecordMetadata metadata) {

    }

    @Override
    public void txBegin() {

    }

    @Override
    public void sendOffsetsToTransaction(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) {

    }

    @Override
    public void txCommit() {

    }

    @Override
    public void txRollback(@Nullable Throwable e) {

    }
}
