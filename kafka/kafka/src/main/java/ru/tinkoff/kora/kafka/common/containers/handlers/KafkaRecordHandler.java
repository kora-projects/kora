package ru.tinkoff.kora.kafka.common.containers.handlers;

import org.apache.kafka.clients.consumer.ConsumerRecord;

@FunctionalInterface
public interface KafkaRecordHandler<K,V> {

    /**
     * @param records consumed records to handle by kafka consumer
     */
    void handle(ConsumerRecord<K,V> records);
}
