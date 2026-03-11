package io.koraframework.kafka.common.producer.serializer;

import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Serializer;
import io.koraframework.json.common.JsonWriter;

public final class JsonKafkaSerializer<T> implements Serializer<T> {
    private final JsonWriter<T> writer;

    public JsonKafkaSerializer(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public byte[] serialize(String topic, T data) {
        try {
            return this.writer.toByteArray(data);
        } catch (Exception e) {
            throw new SerializationException("Unable to serialize into json", e);
        }
    }
}
