package io.koraframework.nats.common.consumer.deserializer;

import io.koraframework.common.annotation.Mapping;
import io.nats.client.Message;

@FunctionalInterface
public interface NatsDeserializer<T> extends Mapping.MappingFunction {
    T deserialize(Message message);
}
