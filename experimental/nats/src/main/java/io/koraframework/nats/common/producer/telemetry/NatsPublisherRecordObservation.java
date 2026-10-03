package io.koraframework.nats.common.producer.telemetry;

import io.koraframework.nats.common.producer.NatsPublishCallback;
import io.nats.client.Message;
import io.nats.client.api.PublishAck;
import org.jspecify.annotations.Nullable;

public interface NatsPublisherRecordObservation extends NatsPublisherOperationObservation, NatsPublishCallback {
    void observeData(@Nullable Object value);

    void observeRecord(Message message);

    default void observeResponse(Message message) {
        observeResult(message);
    }

    @Override
    default void onCompletion(@Nullable PublishAck ack, @Nullable Throwable error) {
        observeResult(ack);
        end(error);
    }
}
