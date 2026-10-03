package io.koraframework.nats.common.consumer.telemetry;

public interface NatsConsumerTelemetry {
    NatsConsumerOperationObservation observeOperation(String operation);

    NatsConsumerPollObservation observePoll();

    void reportPending(String subject, long messages, long bytes);

    void reportLag(String stream, String consumer, long pending);

    default void close() {
    }
}
