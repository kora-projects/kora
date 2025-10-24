package ru.tinkoff.kora.kafka.common.producer.telemetry;

import io.opentelemetry.api.trace.Span;
import jakarta.annotation.Nullable;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public class NoopKafkaPublisherRecordObservation implements KafkaPublisherTelemetry.KafkaPublisherRecordObservation {
    public static final NoopKafkaPublisherRecordObservation INSTANCE = new NoopKafkaPublisherRecordObservation();

    @Override
    public void onCompletion(@Nullable RecordMetadata metadata, @Nullable Exception exception) {}

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

    @Override
    public void observeData(@Nullable Object key, @Nullable Object value) {

    }

    @Override
    public void observeRecord(ProducerRecord<byte[], byte[]> record) {

    }
}
