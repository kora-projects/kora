package io.koraframework.nats.common.producer;

import io.koraframework.nats.common.producer.telemetry.NatsPublisherOperationObservation;
import io.koraframework.nats.common.producer.telemetry.NatsPublisherRecordObservation;
import io.nats.client.Message;

/**
 * Transport hook used by generated publishers bound to an atomic batch.
 */
public interface NatsAtomicBatchSink {

    NatsPublisherOperationObservation observation();

    void append(Message message, NatsPublisherRecordObservation observation);

    void fail(Throwable cause);
}
