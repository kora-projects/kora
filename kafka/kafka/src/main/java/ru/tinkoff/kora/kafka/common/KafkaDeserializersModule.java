package ru.tinkoff.kora.kafka.common;

import org.apache.kafka.common.serialization.*;
import org.apache.kafka.common.utils.Bytes;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Default Kafka deserializes provided by module for base types
 */
public interface KafkaDeserializersModule {
    default Deserializer<String> stringDeserializer() {
        return new StringDeserializer();
    }

    default Deserializer<UUID> uuidDeserializer() {
        return new UUIDDeserializer();
    }

    default Deserializer<byte[]> byteArrayDeserializer() {
        return new ByteArrayDeserializer();
    }

    default Deserializer<Bytes> bytesDeserializer() {
        return new BytesDeserializer();
    }

    default Deserializer<ByteBuffer> byteBufferDeserializer() {
        return new ByteBufferDeserializer();
    }

    default Deserializer<Double> doubleDeserializer() {
        return new DoubleDeserializer();
    }

    default Deserializer<Float> floatDeserializer() {
        return new FloatDeserializer();
    }

    default Deserializer<Integer> integerDeserializer() {
        return new IntegerDeserializer();
    }

    default Deserializer<Long> longDeserializer() {
        return new LongDeserializer();
    }

    default Deserializer<Short> shortDeserializer() {
        return new ShortDeserializer();
    }

    default Deserializer<Void> voidDeserializer() {
        return new VoidDeserializer();
    }
}
