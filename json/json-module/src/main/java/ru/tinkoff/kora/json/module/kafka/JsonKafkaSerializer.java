package ru.tinkoff.kora.json.module.kafka;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import ru.tinkoff.kora.json.common.JsonWriter;

import java.io.IOException;

public final class JsonKafkaSerializer<T> implements Serializer<T> {
    private final JsonWriter<T> writer;

    public JsonKafkaSerializer(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public byte[] serialize(String topic, T data) {
        try {
            return this.writer.toByteArray(data);
        } catch (IOException e) {
            throw new SerializationException("Unable to serialize into json", e);
        }
    }
}
