package ru.tinkoff.kora.json.common;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;

import java.util.List;

public class ListJsonWriter<T> implements JsonWriter<List<T>> {
    private final JsonWriter<T> writer;

    public ListJsonWriter(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public void write(JsonGenerator gen, @Nullable List<T> object) {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeStartArray(object, object.size());
            for (var element : object) {
                this.writer.write(gen, element);
            }
            gen.writeEndArray();
        }
    }
}
