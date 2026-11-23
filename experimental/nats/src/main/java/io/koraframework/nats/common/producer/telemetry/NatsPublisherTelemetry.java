package io.koraframework.nats.common.producer.telemetry;

public interface NatsPublisherTelemetry {
    NatsPublisherOperationObservation observeOperation(String operation);

    NatsPublisherRecordObservation observeSend(String subject);

    NatsPublisherRecordObservation observeRequest(String subject);
}
