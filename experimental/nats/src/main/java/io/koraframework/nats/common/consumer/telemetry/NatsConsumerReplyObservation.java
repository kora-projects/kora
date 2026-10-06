package io.koraframework.nats.common.consumer.telemetry;

import io.nats.client.Message;
import org.jspecify.annotations.Nullable;

public interface NatsConsumerReplyObservation extends NatsConsumerOperationObservation {
    void observeData(@Nullable Object value);

    void observeRecord(Message message);
}
