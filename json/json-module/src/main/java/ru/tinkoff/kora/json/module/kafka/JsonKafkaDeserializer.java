package ru.tinkoff.kora.json.module.kafka;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import ru.tinkoff.kora.common.util.ByteBufferInputStream;
import ru.tinkoff.kora.json.common.JsonReader;

import java.nio.ByteBuffer;

public final class JsonKafkaDeserializer<T> implements Deserializer<T> {
    private final JsonReader<T> reader;

    public JsonKafkaDeserializer(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return this.reader.read(data);
        } catch (Exception e) {
            throw new SerializationException("Unable to deserialize from json", e);
        }
    }

    @Override
    public T deserialize(String topic, Headers headers, ByteBuffer data) {
        if (data == null) {
            return null;
        }
        if (data.hasArray()) {
            try {
                return this.reader.read(data.array(), data.arrayOffset() + data.position(), data.remaining());
            } catch (Exception e) {
                throw new SerializationException("Unable to deserialize from json", e);
            }
        }
        var position = data.position();
        try (var is = new ByteBufferInputStream(data)) {
            return this.reader.read(is);
        } catch (Exception e) {
            throw new SerializationException("Unable to deserialize from json", e);
        } finally {
            data.position(position);
        }
    }
}
