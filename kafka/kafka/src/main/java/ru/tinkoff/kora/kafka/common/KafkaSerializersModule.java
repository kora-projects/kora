package ru.tinkoff.kora.kafka.common;

import org.apache.kafka.common.serialization.*;
import org.apache.kafka.common.utils.Bytes;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Default Kafka serializes provided by module for base types
 */
public interface KafkaSerializersModule {
    default Serializer<String> stringSerializer() {
        return new StringSerializer();
    }

    default Serializer<byte[]> byteArraySerializer() {
        return new ByteArraySerializer();
    }

    default Serializer<ByteBuffer> byteBufferSerializer() {
        return new ByteBufferSerializer();
    }

    default Serializer<Bytes> bytesSerializer() {
        return new BytesSerializer();
    }

    default Serializer<Double> doubleSerializer() {
        return new DoubleSerializer();
    }

    default Serializer<Float> floatSerializer() {
        return new FloatSerializer();
    }

    default Serializer<Integer> integerSerializer() {
        return new IntegerSerializer();
    }

    default Serializer<Long> longSerializer() {
        return new LongSerializer();
    }

    default Serializer<Short> shortSerializer() {
        return new ShortSerializer();
    }

    default Serializer<UUID> uuidSerializer() {
        return new UUIDSerializer();
    }

    default Serializer<Void> voidSerializer() {
        return new VoidSerializer();
    }
}
