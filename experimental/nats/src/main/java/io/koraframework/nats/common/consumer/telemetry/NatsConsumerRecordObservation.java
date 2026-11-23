package io.koraframework.nats.common.consumer.telemetry;

import io.koraframework.nats.common.consumer.deserializer.NatsDeserializer;
import org.jspecify.annotations.Nullable;

public interface NatsConsumerRecordObservation extends NatsConsumerOperationObservation {
    void observeHandle();

    default void observeDeserializer(NatsDeserializer<?> deserializer) {
    }

    void observeData(@Nullable Object value);

    NatsConsumerOperationObservation observeAck(String acknowledgement);

    NatsConsumerReplyObservation observeReply(String subject);
}
