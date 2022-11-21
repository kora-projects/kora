package ru.tinkoff.kora.kafka.common.containers.handlers.impl;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import ru.tinkoff.kora.kafka.common.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.containers.handlers.KafkaRecordsHandlerWithConsumer;
import ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerTelemetry;

public class ManualCommitRecordsHandler<K, V> implements BaseKafkaRecordsHandler<K, V> {
    private final KafkaConsumerTelemetry<K, V> telemetry;
    private final KafkaRecordsHandlerWithConsumer<K, V> handler;

    public ManualCommitRecordsHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordsHandlerWithConsumer<K, V> handler) {
        this.telemetry = telemetry;
        this.handler = handler;
    }

    @Override
    public void handle(ConsumerRecords<K, V> records, Consumer<K, V> consumer, boolean commitAllowed) {
        var ctx = this.telemetry.get(records);
        try {
            this.handler.handle(records, consumer);
            ctx.close(null);
        } catch (Exception e) {
            ctx.close(e);
            throw e;
        }
    }
}
