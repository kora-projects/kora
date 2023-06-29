package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import javax.annotation.Nullable;

public interface KafkaProducerTracer {
    interface KafkaProducerRecordSpan {
        void close(RecordMetadata metadata);

        void close(Throwable e);
    }

    interface KafkaProducerTxSpan {
        void commit();

        void rollback(@Nullable Throwable e);
    }

    KafkaProducerRecordSpan get(ProducerRecord<?, ?> record);

    KafkaProducerTxSpan tx();
}
