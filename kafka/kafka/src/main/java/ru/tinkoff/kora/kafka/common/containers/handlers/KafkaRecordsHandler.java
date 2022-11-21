package ru.tinkoff.kora.kafka.common.containers.handlers;

import org.apache.kafka.clients.consumer.ConsumerRecords;

@FunctionalInterface
public interface KafkaRecordsHandler<K,V> {

    /**
     * @param records consumed records to handle by kafka consumer
     */
    void handle(ConsumerRecords<K,V> records);
}
