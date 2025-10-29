package ru.tinkoff.kora.kafka.common.consumer.containers.handlers.impl;

import io.opentelemetry.context.Context;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.requests.OffsetFetchResponse;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.common.telemetry.OpentelemetryContext;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerPollObservation;
import ru.tinkoff.kora.kafka.common.exceptions.KafkaSkipRecordException;
import ru.tinkoff.kora.kafka.common.exceptions.SkippableRecordException;
import ru.tinkoff.kora.logging.common.MDC;

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
        var opentelemetryCtx = Context.root().with(observation.span());
        ScopedValue.where(OpentelemetryContext.VALUE, Context.root())
            .where(MDC.VALUE, mdc)
            .where(Observation.VALUE, observation)
            .where(OpentelemetryContext.VALUE, opentelemetryCtx)
            .run(() -> {
                observation.observeRecords(records);
                try {
                    var handler = this.handler.get();
                    for (var record : records) {
                        var recordObservation = observation.observeRecord(record);
                        ScopedValue.where(MDC.VALUE, mdc.fork())
                            .where(Observation.VALUE, recordObservation)
                            .where(OpentelemetryContext.VALUE, opentelemetryCtx.with(recordObservation.span()))
                            .run(() -> {
                                try {
                                    try {
                                        recordObservation.observeHandle();
                                        handler.handle(consumer, recordObservation, record);
                                    } catch (Throwable e) {
                                        recordObservation.observeError(e);
                                        if (!(e instanceof KafkaSkipRecordException) && !(e instanceof SkippableRecordException)) {
                                            throw e;
                                        }
                                    }
                                    if (this.shouldCommit && commitAllowed) {
                                        /**
                                         * The committed offset should be the next message your application will consume, i.e. lastProcessedMessageOffset + 1
                                         * @see org.apache.kafka.clients.consumer.KafkaConsumer#commitSync(Map)
                                         */
                                        var topicAndOffsetAndMeta = Map.of(new TopicPartition(record.topic(), record.partition()),
                                            new OffsetAndMetadata(record.offset() + 1, record.leaderEpoch(), OffsetFetchResponse.NO_METADATA));

                                        try {
                                            consumer.commitSync(topicAndOffsetAndMeta);
                                        } catch (WakeupException e) {
                                            // retry commit if thrown on consumer release
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
