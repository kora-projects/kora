package io.koraframework.nats.common.consumer;

import io.koraframework.nats.common.consumer.telemetry.NatsConsumerPollObservation;

@FunctionalInterface
public interface NatsMessagesHandler<T> {
    void handle(NatsConsumerPollObservation observation, NatsMessages<T> messages) throws Exception;
}
