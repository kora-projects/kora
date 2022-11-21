package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonGenerator;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Set;

public class SetJsonWriter<T> implements JsonWriter<Set<T>> {
    private final JsonWriter<T> writer;

    public SetJsonWriter(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public void write(JsonGenerator gen, @Nullable Set<T> object) throws IOException {
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
