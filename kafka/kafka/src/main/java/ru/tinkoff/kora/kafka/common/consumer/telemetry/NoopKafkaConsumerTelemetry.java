package ru.tinkoff.kora.kafka.common.consumer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.apache.kafka.common.TopicPartition;

public class NoopKafkaConsumerTelemetry implements KafkaConsumerTelemetry{
    @Override
    public MeterRegistry meterRegistry() {
        return new CompositeMeterRegistry();
    }

    @Override
    public KafkaConsumerPollObservation observePoll() {
        return null;
    }

    @Override
    public void reportLag(TopicPartition partition, long lag) {

    }
}
