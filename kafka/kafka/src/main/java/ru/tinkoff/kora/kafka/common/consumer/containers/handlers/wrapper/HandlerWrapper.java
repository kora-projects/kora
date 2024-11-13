package ru.tinkoff.kora.kafka.common.consumer.containers.handlers.wrapper;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.impl.RecordHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.impl.RecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;

public final class HandlerWrapper {

    private HandlerWrapper() {}

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandlerRecord(KafkaConsumerTelemetry<K, V> telemetry, boolean shouldCommit, ValueOf<KafkaRecordHandler<K, V>> handler) {
        return new RecordHandler<>(telemetry, shouldCommit, handler);
    }

    @Deprecated
    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandlerRecords(KafkaConsumerTelemetry<K, V> telemetry, boolean shouldCommit, ValueOf<KafkaRecordsHandler<K, V>> handler) {
        return wrapHandlerRecords(telemetry, shouldCommit, handler, false);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandlerRecords(KafkaConsumerTelemetry<K, V> telemetry, boolean shouldCommit, ValueOf<KafkaRecordsHandler<K, V>> handler, boolean allowEmptyRecords) {
        return new RecordsHandler<>(telemetry, shouldCommit, handler);
    }

    @Deprecated
    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, ValueOf<BaseKafkaRecordsHandler<K, V>> realHandler) {
        return wrapHandler(telemetry, realHandler, false);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, ValueOf<BaseKafkaRecordsHandler<K, V>> realHandler, boolean allowEmptyRecords) {
        return (records, consumer, commitAllowed) -> {
            if (records.isEmpty() && !allowEmptyRecords) {
                return;
            }

            var ctx = telemetry.get(records);
            try {
                realHandler.get().handle(records, consumer, commitAllowed);
                ctx.close(null);
            } catch (Exception e) {
                ctx.close(e);
                throw e;
            }
        };
    }
}
