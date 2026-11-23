package io.koraframework.nats.common.producer.serializer;

import io.koraframework.common.annotation.Mapping;
import io.nats.client.impl.Headers;

@FunctionalInterface
public interface NatsSerializer<T> extends Mapping.MappingFunction {
    byte[] serialize(String subject, Headers headers, T value);
}
