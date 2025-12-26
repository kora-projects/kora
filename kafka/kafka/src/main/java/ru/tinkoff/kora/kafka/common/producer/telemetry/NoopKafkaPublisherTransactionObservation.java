package ru.tinkoff.kora.kafka.common.producer.telemetry;

import io.opentelemetry.api.trace.Span;
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public class NoopKafkaPublisherTransactionObservation implements KafkaPublisherTelemetry.KafkaPublisherTransactionObservation {
    public static final NoopKafkaPublisherTransactionObservation INSTANCE = new NoopKafkaPublisherTransactionObservation();

    @Override
    public void observeOffsets(Map<TopicPartition, OffsetAndMetadata> offsets, ConsumerGroupMetadata groupMetadata) {

    }

    @Override
    public void observeCommit() {

    }

    @Override
    public void observeRollback(@Nullable Throwable e) {

    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {

    }

    @Override
    public void observeError(Throwable e) {

    }
}
