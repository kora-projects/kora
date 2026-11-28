package io.koraframework.kafka.common.consumer.containers.handlers.impl;

import io.opentelemetry.context.Context;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContextStorage;
import io.koraframework.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.containers.handlers.KafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerPollObservation;
import io.koraframework.logging.common.MDC;
import io.koraframework.common.context.KoraContext;
import java.util.function.Supplier;

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

        var baseContext = KoraContext.empty()
            .with(MDC.MDC_KEY, mdc)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, opentelemetryCtx);

        var finalContext = Observation.attach(baseContext, observation);

        KoraContext.runWith(finalContext, new RecordsExecutionRunner<>(this, observation, records, consumer, commitAllowed));
    }

    private static final class RecordsExecutionRunner<K, V> implements Supplier<Void> {
        private final RecordsHandler<K, V> instance;
        private final KafkaConsumerPollObservation observation;
        private final ConsumerRecords<K, V> records;
        private final Consumer<K, V> consumer;
        private final boolean commitAllowed;

        public RecordsExecutionRunner(RecordsHandler<K, V> instance,
                                      KafkaConsumerPollObservation observation,
                                      ConsumerRecords<K, V> records,
                                      Consumer<K, V> consumer,
                                      boolean commitAllowed) {
            this.instance = instance;
            this.observation = observation;
            this.records = records;
            this.consumer = consumer;
            this.commitAllowed = commitAllowed;
        }

        @Override
        public Void get() {
            observation.observeRecords(records);
            try {
                var currentHandler = instance.handler.get();
                currentHandler.handle(consumer, observation, records);
                if (instance.shouldCommit && commitAllowed) {
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
            return null;
        }
    }
}
