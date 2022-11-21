package ru.tinkoff.kora.kafka.common.containers.handlers;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface KafkaRecordKeyValueWithExceptionHandler<K,V> {

    /**
     * Handles record by its key and value
     *
     * @param key of {@link ConsumerRecord#key()}
     * @param value of {@link ConsumerRecord#value()}
     * @param exception Exception thrown by {@link ConsumerRecord#key()} or {@link ConsumerRecord#value()}
     */
    void handle(@Nullable K key, @Nullable V value, @Nullable Exception exception);
}
