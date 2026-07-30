package io.koraframework.kafka.common.producer.telemetry.impl;

import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherRecordObservation;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTelemetry;
import io.koraframework.kafka.common.producer.telemetry.KafkaPublisherTransactionObservation;
import io.koraframework.micrometer.common.NoopMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;

public final class NoopKafkaPublisherTelemetry implements KafkaPublisherTelemetry {

    public static final NoopKafkaPublisherTelemetry INSTANCE = new NoopKafkaPublisherTelemetry();

    private final MeterRegistry meterRegistry = NoopMeterRegistry.INSTANCE;

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
