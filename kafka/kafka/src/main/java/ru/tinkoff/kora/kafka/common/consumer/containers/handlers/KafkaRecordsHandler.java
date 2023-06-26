package ru.tinkoff.kora.kafka.common.consumer.containers.handlers;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry.KafkaConsumerRecordsTelemetryContext;

@FunctionalInterface
public interface KafkaRecordsHandler<K, V> {

    /**
     * @param records consumed records to handle by kafka consumer
     */
    void handle(Consumer<K, V> consumer, KafkaConsumerRecordsTelemetryContext<K, V> telemetry, ConsumerRecords<K, V> records);
}
