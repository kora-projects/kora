package ru.tinkoff.kora.kafka.common.containers.handlers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;

@FunctionalInterface
public interface KafkaRecordHandlerWithConsumer<K,V> {

    /**
     * @param record consumed record to handle by kafka consumer
     * @param consumer consumer that consumer records
     */
    void handle(ConsumerRecord<K,V> record, Consumer<K,V> consumer);
}
