package ru.tinkoff.kora.kafka.common.containers.handlers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

@FunctionalInterface
public interface KafkaRecordsHandlerWithConsumer<K,V> {

    /**
     * @param record consumed record to handle by kafka consumer
     * @param consumer consumer that consumer records
     */
    void handle(ConsumerRecords<K,V> record, Consumer<K,V> consumer);
}
