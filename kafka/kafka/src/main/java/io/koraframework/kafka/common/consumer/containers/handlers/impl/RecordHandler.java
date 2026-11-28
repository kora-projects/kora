package io.koraframework.kafka.common.consumer.containers.handlers.impl;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.requests.OffsetFetchResponse;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContextStorage;
import io.koraframework.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import io.koraframework.kafka.common.consumer.containers.handlers.KafkaRecordHandler;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerPollObservation;
import io.koraframework.kafka.common.exceptions.KafkaSkipRecordException;
import io.koraframework.kafka.common.exceptions.SkippableRecordException;
import io.koraframework.logging.common.MDC;
import io.koraframework.common.context.KoraContext;
import io.opentelemetry.context.Context;

import java.util.Map;

public class RecordHandler<K, V> implements BaseKafkaRecordsHandler<K, V> {
    private final ValueOf<KafkaRecordHandler<K, V>> handler;
    private final boolean shouldCommit;

    public RecordHandler(boolean shouldCommit, ValueOf<KafkaRecordHandler<K, V>> handler) {
        this.handler = handler;
        this.shouldCommit = shouldCommit;
    }

    @Override
    public void handle(KafkaConsumerPollObservation observation, ConsumerRecords<K, V> records, Consumer<K, V> consumer, boolean commitAllowed) {
        if (records.isEmpty()) {
            return;
        }
        var mdc = new MDC();
        var current = KoraContext.current();

        var pollContext = (current != null ? current : KoraContext.empty())
            .with(MDC.MDC_KEY, mdc)
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(observation.span()));

        Observation.attach(pollContext, observation).run(() -> {
            observation.observeRecords(records);
            try {
                var currentHandler = this.handler.get();
                for (var record : records) {
                    var recordObservation = observation.observeRecord(record);

                    var recordContext = KoraContext.current()
                        .with(MDC.MDC_KEY, mdc.fork())
                        .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, Context.current().with(recordObservation.span()));

                    Observation.attach(recordContext, recordObservation).run(() -> {
                        try {
                            try {
                                recordObservation.observeHandle();
                                currentHandler.handle(consumer, recordObservation, record);
                            } catch (Throwable e) {
                                recordObservation.observeError(e);
                                if (!(e instanceof KafkaSkipRecordException) && !(e instanceof SkippableRecordException)) {
                                    KoraContext.current().run(() -> { throw new RuntimeException(e); });
                                }
                            }
                            if (this.shouldCommit && commitAllowed) {
                                var topicAndOffsetAndMeta = Map.of(
                                    new TopicPartition(record.topic(), record.partition()),
                                    new OffsetAndMetadata(record.offset() + 1, record.leaderEpoch(), OffsetFetchResponse.NO_METADATA)
                                );

                                try {
                                    consumer.commitSync(topicAndOffsetAndMeta);
                                } catch (WakeupException e) {
                                    recordObservation.observeError(e);
                                    consumer.commitSync(topicAndOffsetAndMeta);
                                    throw e;
                                } catch (Exception e) {
                                    recordObservation.observeError(e);
                                    throw e;
                                }
                            }
                        } finally {
                            recordObservation.end();
                        }
                    });
                }
            } catch (Exception e) {
                observation.observeError(e);
                throw e;
            } finally {
                observation.end();
            }
        });
    }
}
