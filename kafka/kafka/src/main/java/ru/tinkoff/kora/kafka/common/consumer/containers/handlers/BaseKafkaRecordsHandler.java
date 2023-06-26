package ru.tinkoff.kora.kafka.common.consumer.containers.handlers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;

@FunctionalInterface
public interface BaseKafkaRecordsHandler<K,V> {

    /**
     * @param records consumed records to handle by kafka consumer
     * @param consumer consumer that consumer records
     * @param commitAllowed if true that commit is allowed for consumer
     */
    void handle(ConsumerRecords<K, V> records, Consumer<K, V> consumer, boolean commitAllowed);
}
