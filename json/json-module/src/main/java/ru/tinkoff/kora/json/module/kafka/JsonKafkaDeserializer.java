package ru.tinkoff.kora.json.module.kafka;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import ru.tinkoff.kora.json.common.JsonReader;

import java.io.IOException;

public final class JsonKafkaDeserializer<T> implements Deserializer<T> {
    private final JsonReader<T> reader;

    public JsonKafkaDeserializer(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public T deserialize(String topic, byte[] data) {
        try {
            return this.reader.read(data);
        } catch (IOException e) {
            throw new SerializationException("Unable to deserialize from json", e);
        }
    }
}
