package io.koraframework.nats.common.producer;

import io.nats.client.impl.Headers;
import org.jspecify.annotations.Nullable;

public record NatsProducerMessage<T>(String subject, @Nullable String replyTo, @Nullable Headers headers, T value) {
    public NatsProducerMessage(String subject, T value) {
        this(subject, null, null, value);
    }

    public NatsProducerMessage(String subject, Headers headers, T value) {
        this(subject, null, headers, value);
    }
}
