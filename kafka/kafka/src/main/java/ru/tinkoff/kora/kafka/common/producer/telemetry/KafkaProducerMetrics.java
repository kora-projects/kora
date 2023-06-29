package ru.tinkoff.kora.kafka.common.producer.telemetry;

import javax.annotation.Nullable;

public interface KafkaProducerMetrics {
    KafkaProducerTxMetrics tx();

    interface KafkaProducerTxMetrics {
        void commit();

        void rollback(@Nullable Throwable e);
    }

}
