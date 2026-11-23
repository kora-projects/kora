package io.koraframework.nats.common.producer.serializer;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;
import io.koraframework.nats.common.exceptions.NatsSerializationException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public interface NatsSerializersModule {
    @DefaultComponent
    default NatsSerializer<Void> voidNatsSerializer() {
        return (subject, headers, value) -> null;
    }

    @DefaultComponent
    default NatsSerializer<byte[]> byteArrayNatsSerializer() {
        return (subject, headers, value) -> value;
    }

    @DefaultComponent
    default NatsSerializer<String> stringNatsSerializer() {
        return (subject, headers, value) -> value.getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default NatsSerializer<ByteBuffer> byteBufferNatsSerializer() {
        return (subject, headers, value) -> {
            var copy = value.duplicate();
            var result = new byte[copy.remaining()];
            copy.get(result);
            return result;
        };
    }

    @DefaultComponent
    default NatsSerializer<UUID> uuidNatsSerializer() {
        return (subject, headers, value) -> value.toString().getBytes(StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default NatsSerializer<Short> shortNatsSerializer() {
        return (subject, headers, value) -> ByteBuffer.allocate(2).putShort(value).array();
    }

    @DefaultComponent
    default NatsSerializer<Integer> integerNatsSerializer() {
        return (subject, headers, value) -> ByteBuffer.allocate(4).putInt(value).array();
    }

    @DefaultComponent
    default NatsSerializer<Long> longNatsSerializer() {
        return (subject, headers, value) -> ByteBuffer.allocate(8).putLong(value).array();
    }

    @DefaultComponent
    default NatsSerializer<Float> floatNatsSerializer() {
        return (subject, headers, value) -> ByteBuffer.allocate(4).putFloat(value).array();
    }

    @DefaultComponent
    default NatsSerializer<Double> doubleNatsSerializer() {
        return (subject, headers, value) -> ByteBuffer.allocate(8).putDouble(value).array();
    }

    @Json
    @DefaultComponent
    default <T> NatsSerializer<T> jsonNatsSerializer(JsonWriter<T> writer) {
        return (subject, headers, value) -> {
            try {
                return writer.toByteArray(value);
            } catch (RuntimeException e) {
                throw new NatsSerializationException("Failed to serialize NATS JSON on " + subject, e);
            }
        };
    }
}
