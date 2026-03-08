package io.koraframework.kafka.common.consumer.containers.handlers.wrapper;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.containers.handlers.KafkaRecordHandler;
import io.koraframework.kafka.common.consumer.containers.handlers.KafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.containers.handlers.impl.RecordHandler;
import io.koraframework.kafka.common.consumer.containers.handlers.impl.RecordsHandler;

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
