package ru.tinkoff.kora.kafka.common.containers.handlers.wrapper;

import ru.tinkoff.kora.kafka.common.containers.handlers.*;
import ru.tinkoff.kora.kafka.common.containers.handlers.impl.*;
import ru.tinkoff.kora.kafka.common.telemetry.KafkaConsumerTelemetry;

public class HandlerWrapper {
    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordHandler<K, V> realHandler) {
        return new AutoCommitRecordHandler<>(telemetry, realHandler);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordsHandler<K, V> realHandler) {
        return new AutoCommitRecordsHandler<>(telemetry, realHandler);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordKeyValueHandler<K, V> realHandler) {
        return new RecordKeyValueHandler<>(telemetry, realHandler);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordKeyValueWithExceptionHandler<K, V> realHandler) {
        return new RecordKeyValueWithExceptionHandler<>(telemetry, realHandler);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordValueHandler<V> realHandler) {
        return new RecordValueHandler<>(telemetry, realHandler);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordValueWithExceptionHandler<V> realHandler) {
        return new RecordValueWithExceptionHandler<>(telemetry, realHandler);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordHandlerWithConsumer<K, V> realHandler) {
        return new ManualCommitRecordHandler<>(telemetry, realHandler);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, KafkaRecordsHandlerWithConsumer<K, V> realHandler) {
        return new ManualCommitRecordsHandler<>(telemetry, realHandler);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(KafkaConsumerTelemetry<K, V> telemetry, BaseKafkaRecordsHandler<K, V> realHandler) {
        return (records, consumer, commitAllowed) -> {
            var ctx = telemetry.get(records);
            try {
                realHandler.handle(records, consumer, commitAllowed);
                ctx.close(null);
            } catch (Exception e) {
                ctx.close(e);
                throw e;
            }
        };
    }
}
