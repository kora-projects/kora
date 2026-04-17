package io.koraframework.kafka.common.producer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;

public final class NoopKafkaPublisherTelemetry implements KafkaPublisherTelemetry {

    public static final NoopKafkaPublisherTelemetry INSTANCE = new NoopKafkaPublisherTelemetry();

    private final MeterRegistry meterRegistry = new CompositeMeterRegistry();

    private NoopKafkaPublisherTelemetry() {}

    @Override
    public MeterRegistry meterRegistry() {
        return meterRegistry;
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
