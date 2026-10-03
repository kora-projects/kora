package io.koraframework.nats.common.consumer.telemetry;

import io.koraframework.nats.common.consumer.NatsMessages;
import io.nats.client.Message;

public interface NatsConsumerPollObservation extends NatsConsumerOperationObservation {
    void observeRecords(NatsMessages<?> records);

    NatsConsumerRecordObservation observeRecord(Message message);
}
