package ru.tinkoff.kora.kafka.common.containers.handlers.impl;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import ru.tinkoff.kora.kafka.common.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.containers.handlers.KafkaRecordKeyValueHandler;
import ru.tinkoff.kora.kafka.common.containers.handlers.KafkaRecordKeyValueWithExceptionHandler;
import ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerTelemetry;

public class RecordKeyValueWithExceptionHandler<K, V> implements BaseKafkaRecordsHandler<K, V> {
    private final KafkaConsumerTelemetry<K, V> telemetry;
    private final KafkaRecordKeyValueWithExceptionHandler<K, V> handler;

    public RecordKeyValueWithExceptionHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordKeyValueWithExceptionHandler<K, V> handler) {
        this.telemetry = telemetry;
        this.handler = handler;
    }

    @Override
    public void handle(ConsumerRecords<K, V> records, Consumer<K, V> consumer, boolean commitAllowed) {
        var ctx = this.telemetry.get(records);
        try {
            for (var record : records) {
                var recordCtx = ctx.get(record);
                K key;
                try {
                    key = record.key();
                } catch (Exception e) {
                    try {
                        this.handler.handle(null, null, e);
                        recordCtx.close(null);
                        return;
                    } catch (Exception finalException) {
                        recordCtx.close(finalException);
                        throw finalException;
                    }
                }
                try {
                    this.handler.handle(key, record.value(), null);
                    recordCtx.close(null);
                } catch (Exception e) {
                    try {
                        this.handler.handle(key, null, e);
                        recordCtx.close(null);
                    } catch (Exception finalException) {
                        recordCtx.close(finalException);
                        throw finalException;
                    }
                }
                if (commitAllowed) {
                    consumer.commitSync();
                }
                ctx.close(null);
            }
        } catch (Exception e) {
            ctx.close(e);
            throw e;
        }
    }
}
