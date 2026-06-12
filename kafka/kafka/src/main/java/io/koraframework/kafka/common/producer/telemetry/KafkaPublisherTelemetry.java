package io.koraframework.kafka.common.producer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;

public interface KafkaPublisherTelemetry {

    MeterRegistry meterRegistry();

    KafkaPublisherTransactionObservation observeTx();

    KafkaPublisherRecordObservation observeSend(String topic);
}
