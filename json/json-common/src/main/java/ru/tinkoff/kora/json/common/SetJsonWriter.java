package ru.tinkoff.kora.json.common;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;

import java.util.Set;

public class SetJsonWriter<T> implements JsonWriter<Set<T>> {
    private final JsonWriter<T> writer;

    public SetJsonWriter(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public void write(JsonGenerator gen, @Nullable Set<T> object) {
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
