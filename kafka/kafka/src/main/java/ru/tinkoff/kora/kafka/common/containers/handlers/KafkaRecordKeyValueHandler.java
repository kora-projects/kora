package ru.tinkoff.kora.kafka.common.containers.handlers;

import org.apache.kafka.clients.consumer.ConsumerRecord;

@FunctionalInterface
public interface KafkaRecordKeyValueHandler<K,V> {

    /**
     * Handles record by its key and value
     *
     * @param key of {@link ConsumerRecord#key()}
     * @param value of {@link ConsumerRecord#value()}
     */
    void handle(K key, V value);
}
