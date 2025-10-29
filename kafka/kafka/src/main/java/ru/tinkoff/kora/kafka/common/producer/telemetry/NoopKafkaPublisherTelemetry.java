package ru.tinkoff.kora.kafka.common.producer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

public class NoopKafkaPublisherTelemetry implements KafkaPublisherTelemetry {
    public static final NoopKafkaPublisherTelemetry INSTANCE = new  NoopKafkaPublisherTelemetry();

    @Override
    public MeterRegistry meterRegistry() {
        return new CompositeMeterRegistry();
    }

    @Override
    public KafkaPublisherTransactionObservation observeTx() {
        return NoopKafkaPublisherTransactionObservation.INSTANCE;
    }

    @Override
    public KafkaPublisherRecordObservation observeSend(String topic) {
        return NoopKafkaPublisherRecordObservation.INSTANCE;
    }
}
