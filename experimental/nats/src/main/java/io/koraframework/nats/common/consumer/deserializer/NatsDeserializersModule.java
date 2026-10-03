package io.koraframework.nats.common.consumer.deserializer;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.annotation.Json;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public interface NatsDeserializersModule {
    @DefaultComponent
    default NatsDeserializer<Void> voidNatsDeserializer() {
        return message -> null;
    }

    @DefaultComponent
    default NatsDeserializer<byte[]> byteArrayNatsDeserializer() {
        return message -> message.getData();
    }

    @DefaultComponent
    default NatsDeserializer<String> stringNatsDeserializer() {
        return message -> new String(message.getData(), StandardCharsets.UTF_8);
    }

    @DefaultComponent
    default NatsDeserializer<ByteBuffer> byteBufferNatsDeserializer() {
        return message -> ByteBuffer.wrap(message.getData());
    }

    @DefaultComponent
    default NatsDeserializer<UUID> uuidNatsDeserializer() {
        return message -> UUID.fromString(new String(message.getData(), StandardCharsets.UTF_8));
    }

    @DefaultComponent
    default NatsDeserializer<Short> shortNatsDeserializer() {
        return message -> {
            if (message.getData().length != 2) {
                throw new IllegalArgumentException("NATS Short payload must contain 2 bytes");
            }
            return ByteBuffer.wrap(message.getData()).getShort();
        };
    }

    @DefaultComponent
    default NatsDeserializer<Integer> integerNatsDeserializer() {
        return message -> {
            if (message.getData().length != 4) {
                throw new IllegalArgumentException("NATS Integer payload must contain 4 bytes");
            }
            return ByteBuffer.wrap(message.getData()).getInt();
        };
    }

    @DefaultComponent
    default NatsDeserializer<Long> longNatsDeserializer() {
        return message -> {
            if (message.getData().length != 8) {
                throw new IllegalArgumentException("NATS Long payload must contain 8 bytes");
            }
            return ByteBuffer.wrap(message.getData()).getLong();
        };
    }

    @DefaultComponent
    default NatsDeserializer<Float> floatNatsDeserializer() {
        return message -> {
            if (message.getData().length != 4) {
                throw new IllegalArgumentException("NATS Float payload must contain 4 bytes");
            }
            return ByteBuffer.wrap(message.getData()).getFloat();
        };
    }

    @DefaultComponent
    default NatsDeserializer<Double> doubleNatsDeserializer() {
        return message -> {
            if (message.getData().length != 8) {
                throw new IllegalArgumentException("NATS Double payload must contain 8 bytes");
            }
            return ByteBuffer.wrap(message.getData()).getDouble();
        };
    }

    @Json
    @DefaultComponent
    default <T> NatsDeserializer<T> jsonNatsDeserializer(JsonReader<T> reader) {
        return new JsonNatsDeserializer<>(reader);
    }
}
