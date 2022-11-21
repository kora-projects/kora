package ru.tinkoff.kora.kafka.common.containers.handlers;

import org.apache.kafka.clients.consumer.ConsumerRecord;

@FunctionalInterface
public interface KafkaRecordValueHandler<V> {

    /**
     * Handles record by its value
     *
     * @param value of {@link ConsumerRecord#value()}
     */
    void handle(V value);
}
