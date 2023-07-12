package ru.tinkoff.kora.kafka.common;

import org.apache.kafka.common.serialization.*;
import org.apache.kafka.common.utils.Bytes;
import ru.tinkoff.kora.common.DefaultComponent;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Default Kafka deserializes provided by module for base types
 */
public interface KafkaDeserializersModule {
    @DefaultComponent
    default Deserializer<String> stringDeserializer() {
        return new StringDeserializer();
    }

    @DefaultComponent
    default Deserializer<UUID> uuidDeserializer() {
        return new UUIDDeserializer();
    }

    @DefaultComponent
    default Deserializer<byte[]> byteArrayDeserializer() {
        return new ByteArrayDeserializer();
    }

    @DefaultComponent
    default Deserializer<Bytes> bytesDeserializer() {
        return new BytesDeserializer();
    }

    @DefaultComponent
    default Deserializer<ByteBuffer> byteBufferDeserializer() {
        return new ByteBufferDeserializer();
    }

    @DefaultComponent
    default Deserializer<Double> doubleDeserializer() {
        return new DoubleDeserializer();
    }

    @DefaultComponent
    default Deserializer<Float> floatDeserializer() {
        return new FloatDeserializer();
    }

    @DefaultComponent
    default Deserializer<Integer> integerDeserializer() {
        return new IntegerDeserializer();
    }

    @DefaultComponent
    default Deserializer<Long> longDeserializer() {
        return new LongDeserializer();
    }

    @DefaultComponent
    default Deserializer<Short> shortDeserializer() {
        return new ShortDeserializer();
    }

    @DefaultComponent
    default Deserializer<Void> voidDeserializer() {
        return new VoidDeserializer();
    }
}
