package ru.tinkoff.kora.kafka.common.consumer.containers.handlers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerPollObservation;

@FunctionalInterface
public interface KafkaRecordsHandler<K, V> {

    /**
     * @param records consumed records to handle by kafka consumer
     */
    void handle(Consumer<K, V> consumer, KafkaConsumerPollObservation observation, ConsumerRecords<K, V> records);
}
