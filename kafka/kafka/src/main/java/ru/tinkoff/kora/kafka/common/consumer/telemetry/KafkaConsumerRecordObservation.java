package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import ru.tinkoff.kora.common.telemetry.Observation;

public interface KafkaConsumerRecordObservation extends Observation {
    void observeHandle();
}
