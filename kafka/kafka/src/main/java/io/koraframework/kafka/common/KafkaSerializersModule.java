package io.koraframework.kafka.common;

import org.apache.kafka.common.serialization.*;
import org.apache.kafka.common.utils.Bytes;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.json.common.JsonWriter;
import io.koraframework.json.common.annotation.Json;
import io.koraframework.kafka.common.producer.serializer.JsonKafkaSerializer;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Default Kafka serializes provided by module for base types
 */
public interface KafkaSerializersModule {

    @DefaultComponent
    default Serializer<String> stringKafkaSerializer() {
        return new StringSerializer();
    }

    @DefaultComponent
    default Serializer<byte[]> byteArrayKafkaSerializer() {
        return new ByteArraySerializer();
    }

    @DefaultComponent
    default Serializer<ByteBuffer> byteBufferKafkaSerializer() {
        return new ByteBufferSerializer();
    }

    @DefaultComponent
    default Serializer<Bytes> bytesKafkaSerializer() {
        return new BytesSerializer();
    }

    @DefaultComponent
    default Serializer<Double> doubleKafkaSerializer() {
        return new DoubleSerializer();
    }

    @DefaultComponent
    default Serializer<Float> floatKafkaSerializer() {
        return new FloatSerializer();
    }

    @DefaultComponent
    default Serializer<Integer> integerKafkaSerializer() {
        return new IntegerSerializer();
    }

    @DefaultComponent
    default Serializer<Long> longKafkaSerializer() {
        return new LongSerializer();
    }

    @DefaultComponent
    default Serializer<Short> shortKafkaSerializer() {
        return new ShortSerializer();
    }

    @DefaultComponent
    default Serializer<UUID> uuidKafkaSerializer() {
        return new UUIDSerializer();
    }

    @DefaultComponent
    default Serializer<Void> voidKafkaSerializer() {
        return new VoidSerializer();
    }

    @Json
    @DefaultComponent
    default <T> Serializer<T> jsonKafkaSerializer(JsonWriter<T> writer) {
        return new JsonKafkaSerializer<>(writer);
    }
}
