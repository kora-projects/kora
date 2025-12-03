package ru.tinkoff.kora.json.common;

import jakarta.annotation.Nullable;
import tools.jackson.core.JsonGenerator;

import java.util.Map;

public class MapJsonWriter<T> implements JsonWriter<Map<String, T>> {
    private final JsonWriter<T> writer;

    public MapJsonWriter(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public void write(JsonGenerator gen, @Nullable Map<String, T> object) {
        if (object == null) {
            gen.writeNull();
            return;
        }
        gen.writeStartObject(object, object.size());
        for (var field : object.entrySet()) {
            gen.writeName(field.getKey());
            this.writer.write(gen, field.getValue());
        }
        gen.writeEndObject();
    }
}
