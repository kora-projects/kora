package io.koraframework.kafka.common.consumer.telemetry;

import io.koraframework.common.telemetry.Observation;

public interface KafkaConsumerRecordObservation extends Observation {
    void observeHandle();
}
