package ru.tinkoff.kora.kafka.common.telemetry;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import javax.annotation.Nullable;

public interface KafkaConsumerTracer {
    interface KafkaConsumerRecordsSpan {
        KafkaConsumerRecordSpan get(ConsumerRecord<?, ?> record);

        void close(long duration, @Nullable Throwable ex);
    }

    interface KafkaConsumerRecordSpan {
        void close(long duration, @Nullable Throwable ex);
    }

    KafkaConsumerRecordsSpan get(ConsumerRecords<?, ?> records);
}
