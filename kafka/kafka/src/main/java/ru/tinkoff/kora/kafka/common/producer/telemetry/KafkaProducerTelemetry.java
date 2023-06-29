package ru.tinkoff.kora.kafka.common.producer.telemetry;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import javax.annotation.Nullable;

public interface KafkaProducerTelemetry extends AutoCloseable {
    @Override
    void close();

    KafkaProducerTransactionTelemetryContext tx();

    KafkaProducerRecordTelemetryContext record(ProducerRecord<?, ?> record);

    interface KafkaProducerTransactionTelemetryContext {
        void commit();

        void rollback(@Nullable Throwable e);
    }

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
