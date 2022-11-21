package ru.tinkoff.kora.kafka.common.containers.handlers.impl;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import ru.tinkoff.kora.kafka.common.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.containers.handlers.KafkaRecordHandlerWithConsumer;
import ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerTelemetry;

public class ManualCommitRecordHandler<K, V> implements BaseKafkaRecordsHandler<K, V> {
    private final KafkaConsumerTelemetry<K, V> telemetry;
    private final KafkaRecordHandlerWithConsumer<K, V> handler;

    public ManualCommitRecordHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordHandlerWithConsumer<K, V> handler) {
        this.telemetry = telemetry;
        this.handler = handler;
    }

    @Override
    public void handle(ConsumerRecords<K, V> records, Consumer<K, V> consumer, boolean commitAllowed) {
        var ctx = this.telemetry.get(records);
        try {
            for (var record : records) {
                var recordCtx = ctx.get(record);
                try {
                    this.handler.handle(record, consumer);
                    recordCtx.close(null);
                } catch (Exception e) {
                    recordCtx.close(e);
                    throw e;
                }
            }
            ctx.close(null);
        } catch (Exception e) {
            ctx.close(e);
            throw e;
        }
    }
}
