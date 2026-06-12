package io.koraframework.kafka.common.consumer.telemetry.impl;

import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerPollObservation;
import io.koraframework.kafka.common.consumer.telemetry.KafkaConsumerTelemetry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.apache.kafka.common.TopicPartition;

public final class NoopKafkaConsumerTelemetry implements KafkaConsumerTelemetry {

    public static final NoopKafkaConsumerTelemetry INSTANCE = new NoopKafkaConsumerTelemetry();

    private final MeterRegistry meterRegistry = new CompositeMeterRegistry();

    @Override
    public MeterRegistry meterRegistry() {
        return meterRegistry;
    }

    @Override
    public KafkaConsumerPollObservation observePoll() {
        return NoopKafkaConsumerPollObservation.INSTANCE;
    }

    @Override
    public void reportLag(TopicPartition partition, long lag) {

    }
}
