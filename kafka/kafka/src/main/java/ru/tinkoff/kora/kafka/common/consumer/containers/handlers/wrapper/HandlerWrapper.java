package ru.tinkoff.kora.kafka.common.consumer.containers.handlers.wrapper;

import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.impl.RecordHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.impl.RecordsHandler;

public final class HandlerWrapper {

    private HandlerWrapper() {}

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandlerRecord(boolean shouldCommit, ValueOf<KafkaRecordHandler<K, V>> handler) {
        return new RecordHandler<>(shouldCommit, handler);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandlerRecords(boolean shouldCommit, ValueOf<KafkaRecordsHandler<K, V>> handler, boolean allowEmptyRecords) {
        return new RecordsHandler<>(shouldCommit, handler, allowEmptyRecords);
    }

    public static <K, V> BaseKafkaRecordsHandler<K, V> wrapHandler(ValueOf<BaseKafkaRecordsHandler<K, V>> realHandler, boolean allowEmptyRecords) {
        return (observation, records, consumer, commitAllowed) -> {
            if (records.isEmpty() && !allowEmptyRecords) {
                return;
            }

            realHandler.get().handle(observation, records, consumer, commitAllowed);
        };
    }
}
