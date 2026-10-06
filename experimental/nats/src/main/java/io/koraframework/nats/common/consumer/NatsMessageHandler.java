package io.koraframework.nats.common.consumer;

import io.koraframework.nats.common.consumer.telemetry.NatsConsumerPollObservation;

@FunctionalInterface
public interface NatsMessageHandler<T> {
    void handle(NatsConsumerPollObservation observation, NatsMessage<T> message) throws Exception;
}
