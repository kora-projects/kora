package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;

public interface KafkaConsumerTelemetry {
    MeterRegistry meterRegistry();

    KafkaConsumerPollObservation observePoll();

    void reportLag(TopicPartition partition, long lag);
}
