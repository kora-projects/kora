package ru.tinkoff.kora.kafka.common.containers.handlers.impl;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import ru.tinkoff.kora.kafka.common.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.containers.handlers.KafkaRecordValueHandler;
import ru.tinkoff.kora.kafka.common.containers.handlers.KafkaRecordValueWithExceptionHandler;
import ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerTelemetry;

public class RecordValueWithExceptionHandler<K, V> implements BaseKafkaRecordsHandler<K, V> {
    private final KafkaConsumerTelemetry<K, V> telemetry;
    private final KafkaRecordValueWithExceptionHandler<V> handler;

    public RecordValueWithExceptionHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordValueWithExceptionHandler<V> handler) {
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
                    this.handler.handle(record.value(), null);
                    recordCtx.close(null);
                } catch (Exception e) {
                    try {
                        this.handler.handle(null, e);
                        recordCtx.close(null);
                    } catch (Exception finalException) {
                        recordCtx.close(finalException);
                        throw finalException;
                    }
                }
            }
            if (commitAllowed) {
                consumer.commitSync();
            }
            ctx.close(null);
        } catch (Exception e) {
            ctx.close(e);
            throw e;
        }
    }
}
