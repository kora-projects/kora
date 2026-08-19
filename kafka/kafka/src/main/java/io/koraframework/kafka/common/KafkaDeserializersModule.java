package io.koraframework.kafka.common;

import org.apache.kafka.common.serialization.*;
import org.apache.kafka.common.utils.Bytes;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.json.common.JsonReader;
import io.koraframework.json.common.annotation.Json;
import io.koraframework.kafka.common.consumer.deserializer.JsonKafkaDeserializer;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Default Kafka deserializes provided by module for base types
 */
public interface KafkaDeserializersModule {

    @DefaultComponent
    default Deserializer<String> stringKafkaDeserializer() {
        return new StringDeserializer();
    }

    @DefaultComponent
    default Deserializer<UUID> uuidKafkaDeserializer() {
        return new UUIDDeserializer();
    }

    @DefaultComponent
    default Deserializer<byte[]> byteArrayKafkaDeserializer() {
        return new ByteArrayDeserializer();
    }

    @DefaultComponent
    default Deserializer<Bytes> bytesKafkaDeserializer() {
        return new BytesDeserializer();
    }

    @DefaultComponent
    default Deserializer<ByteBuffer> byteBufferKafkaDeserializer() {
        return new ByteBufferDeserializer();
    }

    @DefaultComponent
    default Deserializer<Double> doubleKafkaDeserializer() {
        return new DoubleDeserializer();
    }

    @DefaultComponent
    default Deserializer<Float> floatKafkaDeserializer() {
        return new FloatDeserializer();
    }

    @DefaultComponent
    default Deserializer<Integer> integerKafkaDeserializer() {
        return new IntegerDeserializer();
    }

    @DefaultComponent
    default Deserializer<Long> longKafkaDeserializer() {
        return new LongDeserializer();
    }

    @DefaultComponent
    default Deserializer<Short> shortKafkaDeserializer() {
        return new ShortDeserializer();
    }

    @DefaultComponent
    default Deserializer<Void> voidKafkaDeserializer() {
        return new VoidDeserializer();
    }

    @Json
    @DefaultComponent
    default <T> JsonKafkaDeserializer<T> jsonKafkaDeserializer(JsonReader<T> reader) {
        return new JsonKafkaDeserializer<>(reader);
    }
}
