package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import io.opentelemetry.api.trace.Span;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class NoopKafkaConsumerPollObservation implements KafkaConsumerPollObservation {

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
        return new NoopKafkaConsumerRecordObservation();
    }

    @Override
    public void observeRecords(ConsumerRecords<?, ?> records) {

    }
}
