package io.koraframework.nats.common.producer;

import io.nats.client.api.PublishAck;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface NatsPublishCallback {

    /**
     * Core success has null acknowledgement; JetStream success has server PublishAck.
     */
    void onCompletion(@Nullable PublishAck acknowledgement, @Nullable Throwable error);
}
