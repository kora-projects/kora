package ru.tinkoff.kora.kafka.common;

import org.apache.kafka.common.serialization.*;
import org.apache.kafka.common.utils.Bytes;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.json.common.JsonWriter;
import ru.tinkoff.kora.json.common.annotation.Json;
import ru.tinkoff.kora.kafka.common.producer.serializer.JsonKafkaSerializer;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Default Kafka serializes provided by module for base types
 */
public interface KafkaSerializersModule {
    @DefaultComponent
    default Serializer<String> stringSerializer() {
        return new StringSerializer();
    }

    @DefaultComponent
    default Serializer<byte[]> byteArraySerializer() {
        return new ByteArraySerializer();
    }

    @DefaultComponent
    default Serializer<ByteBuffer> byteBufferSerializer() {
        return new ByteBufferSerializer();
    }

    @DefaultComponent
    default Serializer<Bytes> bytesSerializer() {
        return new BytesSerializer();
    }

    @DefaultComponent
    default Serializer<Double> doubleSerializer() {
        return new DoubleSerializer();
    }

    @DefaultComponent
    default Serializer<Float> floatSerializer() {
        return new FloatSerializer();
    }

    @DefaultComponent
    default Serializer<Integer> integerSerializer() {
        return new IntegerSerializer();
    }

    @DefaultComponent
    default Serializer<Long> longSerializer() {
        return new LongSerializer();
    }

    @DefaultComponent
    default Serializer<Short> shortSerializer() {
        return new ShortSerializer();
    }

    @DefaultComponent
    default Serializer<UUID> uuidSerializer() {
        return new UUIDSerializer();
    }

    @DefaultComponent
    default Serializer<Void> voidSerializer() {
        return new VoidSerializer();
    }

    @DefaultComponent
    @Json
    default <T> Serializer<T> objectSerializer(JsonWriter<T> w) {
        return new JsonKafkaSerializer<>(w);
    }
}
