package ru.tinkoff.kora.kafka.common.consumer.containers.handlers.impl;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;

public class RecordsHandler<K, V> implements BaseKafkaRecordsHandler<K, V> {
    private final KafkaConsumerTelemetry<K, V> telemetry;
    private final ValueOf<KafkaRecordsHandler<K, V>> handler;
    private final boolean shouldCommit;
    private final boolean allowEmptyRecords;

    public RecordsHandler(KafkaConsumerTelemetry<K, V> telemetry, boolean shouldCommit, ValueOf<KafkaRecordsHandler<K, V>> handler) {
        this(telemetry, shouldCommit, handler, false);
    }

    public RecordsHandler(KafkaConsumerTelemetry<K, V> telemetry, boolean shouldCommit, ValueOf<KafkaRecordsHandler<K, V>> handler, boolean allowEmptyRecords) {
        this.telemetry = telemetry;
        this.handler = handler;
        this.shouldCommit = shouldCommit;
        this.allowEmptyRecords = allowEmptyRecords;
    }

    @Override
    public void handle(ConsumerRecords<K, V> records, Consumer<K, V> consumer, boolean commitAllowed) {
        if (records.isEmpty() && !allowEmptyRecords) {
            return;
        }

        var ctx = this.telemetry.get(records);
        try {
            var handler = this.handler.get();
            handler.handle(consumer, ctx, records);
            if (this.shouldCommit && commitAllowed) {
                try {
                    consumer.commitSync();
                } catch (WakeupException e) {
                    // retry commit if thrown on consumer release
                    consumer.commitSync();
                }
            }
            ctx.close(consumer.metrics(), null);
        } catch (Exception e) {
            ctx.close(consumer.metrics(), e);
            throw e;
        }
    }
}
