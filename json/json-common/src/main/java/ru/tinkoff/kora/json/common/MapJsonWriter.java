package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonGenerator;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;

public class MapJsonWriter<T> implements JsonWriter<Map<String, T>> {
    private final JsonWriter<T> writer;

    public MapJsonWriter(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public void write(JsonGenerator gen, @Nullable Map<String, T> object) throws IOException {
        if (object == null) {
            gen.writeNull();
            return;
        }
        gen.writeStartObject(object, object.size());
        for (var field : object.entrySet()) {
            gen.writeFieldName(field.getKey());
            this.writer.write(gen, field.getValue());
        }
        gen.writeEndObject();
    }
}
