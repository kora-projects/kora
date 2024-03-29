package ru.tinkoff.kora.kafka.common.consumer.containers.handlers.impl;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;

public class RecordHandler<K, V> implements BaseKafkaRecordsHandler<K, V> {
    private final KafkaConsumerTelemetry<K, V> telemetry;
    private final ValueOf<KafkaRecordHandler<K, V>> handler;
    private final boolean shouldCommit;

    public RecordHandler(KafkaConsumerTelemetry<K, V> telemetry, boolean shouldCommit, ValueOf<KafkaRecordHandler<K, V>> handler) {
        this.telemetry = telemetry;
        this.handler = handler;
        this.shouldCommit = shouldCommit;
    }

    @Override
    public void handle(ConsumerRecords<K, V> records, Consumer<K, V> consumer, boolean commitAllowed) {
        var ctx = this.telemetry.get(records);
        try {
            var handler = this.handler.get();
            for (var record : records) {
                var recordCtx = ctx.get(record);
                try {
                    handler.handle(consumer, recordCtx, record);
                    recordCtx.close(null);
                } catch (Exception e) {
                    recordCtx.close(e);
                    throw e;
                }
            }
            if (this.shouldCommit && commitAllowed) {
                consumer.commitSync();
            }
            ctx.close(null);
        } catch (Exception e) {
            ctx.close(e);
            throw e;
        }
    }
}
