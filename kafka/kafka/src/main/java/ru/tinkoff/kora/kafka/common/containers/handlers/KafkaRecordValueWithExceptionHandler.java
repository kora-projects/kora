package ru.tinkoff.kora.kafka.common.containers.handlers;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jetbrains.annotations.Nullable;

@FunctionalInterface
public interface KafkaRecordValueWithExceptionHandler<V> {

    /**
     * Handles record by its value
     *
     * @param value of {@link ConsumerRecord#value()}
     * @param exception Exception thrown by {@link ConsumerRecord#value()}
     */
    void handle(@Nullable V value, @Nullable Exception exception);
}
