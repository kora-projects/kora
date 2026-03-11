package io.koraframework.kafka.common.consumer.containers.handlers.impl;

import io.opentelemetry.context.Context;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.containers.handlers.KafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerPollObservation;
import io.koraframework.logging.common.MDC;

public class RecordsHandler<K, V> implements BaseKafkaRecordsHandler<K, V> {
    private final ValueOf<KafkaRecordsHandler<K, V>> handler;
    private final boolean shouldCommit;
    private final boolean allowEmptyRecords;

    public RecordsHandler(boolean shouldCommit, ValueOf<KafkaRecordsHandler<K, V>> handler, boolean allowEmptyRecords) {
        this.handler = handler;
        this.shouldCommit = shouldCommit;
        this.allowEmptyRecords = allowEmptyRecords;
    }

    @Override
    public void handle(KafkaConsumerPollObservation observation, ConsumerRecords<K, V> records, Consumer<K, V> consumer, boolean commitAllowed) {
        if (records.isEmpty() && !allowEmptyRecords) {
            return;
        }
        var mdc = new MDC();
        var opentelemetryCtx = Context.root().with(observation.span());
        ScopedValue.where(OpentelemetryContext.VALUE, Context.root())
            .where(MDC.VALUE, mdc)
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, opentelemetryCtx)
            .run(() -> {
                observation.observeRecords(records);
                try {
                    var handler = this.handler.get();
                    handler.handle(consumer, observation, records);
                    if (this.shouldCommit && commitAllowed) {
                        try {
                            consumer.commitSync();
                        } catch (WakeupException e) {
                            // retry commit if thrown on consumer release
                            consumer.commitSync();
                        }
                    }
                } catch (Throwable e) {
                    observation.observeError(e);
                    throw e;
                } finally {
                    observation.end();
                }
            });
    }
}
