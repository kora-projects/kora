package io.koraframework.kafka.common.consumer.telemetry;

import io.opentelemetry.api.trace.Span;

public final class NoopKafkaConsumerRecordObservation implements KafkaConsumerRecordObservation {

    public static final NoopKafkaConsumerRecordObservation INSTANCE = new NoopKafkaConsumerRecordObservation();

    private NoopKafkaConsumerRecordObservation() {}

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
    public void observeHandle() {

    }
}
