package io.koraframework.kafka.common.consumer.telemetry;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.apache.kafka.common.TopicPartition;

public final class NoopKafkaConsumerTelemetry implements KafkaConsumerTelemetry{

    public static final NoopKafkaConsumerTelemetry INSTANCE = new NoopKafkaConsumerTelemetry();

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
