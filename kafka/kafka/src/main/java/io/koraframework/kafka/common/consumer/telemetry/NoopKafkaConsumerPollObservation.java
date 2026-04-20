package io.koraframework.kafka.common.consumer.telemetry;

import io.opentelemetry.api.trace.Span;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public final class NoopKafkaConsumerPollObservation implements KafkaConsumerPollObservation {

    public static final NoopKafkaConsumerPollObservation INSTANCE = new NoopKafkaConsumerPollObservation();

    private NoopKafkaConsumerPollObservation() {}

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
    public KafkaConsumerRecordObservation observeRecord(ConsumerRecord<?, ?> record) {
        return NoopKafkaConsumerRecordObservation.INSTANCE;
    }

    @Override
    public void observeRecords(ConsumerRecords<?, ?> records) {

    }
}
