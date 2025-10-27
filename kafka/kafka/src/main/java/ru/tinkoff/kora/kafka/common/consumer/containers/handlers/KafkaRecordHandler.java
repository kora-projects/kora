package ru.tinkoff.kora.kafka.common.consumer.containers.handlers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerRecordObservation;

@FunctionalInterface
public interface KafkaRecordHandler<K, V> {

    /**
     * @param record consumed record to handle by kafka consumer
     */
    void handle(Consumer<K, V> consumer, KafkaConsumerRecordObservation observation, ConsumerRecord<K, V> record);
}
