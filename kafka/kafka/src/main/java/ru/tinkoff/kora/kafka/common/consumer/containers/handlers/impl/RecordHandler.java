package ru.tinkoff.kora.kafka.common.consumer.containers.handlers.impl;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.requests.OffsetFetchResponse;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.BaseKafkaRecordsHandler;
import ru.tinkoff.kora.kafka.common.consumer.containers.handlers.KafkaRecordHandler;
import ru.tinkoff.kora.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;

import java.util.Map;

public class RecordHandler<K, V> implements BaseKafkaRecordsHandler<K, V> {
    private final KafkaConsumerTelemetry<K, V> telemetry;
    private final ValueOf<KafkaRecordHandler<K, V>> handler;
    private final boolean shouldCommit;

    public RecordHandler(KafkaConsumerTelemetry<K, V> telemetry, boolean shouldCommit, ValueOf<KafkaRecordHandler<K, V>> handler) {
        this.telemetry = telemetry;
        this.handler = handler;
        this.shouldCommit = shouldCommit;
    }

    @Override
    public void handle(ConsumerRecords<K, V> records, Consumer<K, V> consumer, boolean commitAllowed) {
        if (records.isEmpty()) {
            return;
        }

        var ctx = this.telemetry.get(records);
        try {
            var handler = this.handler.get();
            for (var record : records) {
                var recordCtx = ctx.get(record);
                try {
                    handler.handle(consumer, recordCtx, record);
                    if (this.shouldCommit && commitAllowed) {
                        /**
                         * The committed offset should be the next message your application will consume, i.e. lastProcessedMessageOffset + 1
                         * @see org.apache.kafka.clients.consumer.KafkaConsumer#commitSync(Map)
                         */
                        var topicAndOffsetAndMeta = Map.of(new TopicPartition(record.topic(), record.partition()),
                            new OffsetAndMetadata(record.offset() + 1, record.leaderEpoch(), OffsetFetchResponse.NO_METADATA));

                        try {
                            consumer.commitSync(topicAndOffsetAndMeta);
                            recordCtx.close(null);
                        } catch (WakeupException e) {
                            // retry commit if thrown on consumer release
                            consumer.commitSync(topicAndOffsetAndMeta);
                            recordCtx.close(null);
                            throw e;
                        }
                    } else {
                        recordCtx.close(null);
                    }
                } catch (WakeupException e) {
                    throw e;
                } catch (Exception e) {
                    recordCtx.close(e);
                    throw e;
                }
            }
            ctx.close(null);
        } catch (Exception e) {
            ctx.close(e);
            throw e;
        }
    }
}
