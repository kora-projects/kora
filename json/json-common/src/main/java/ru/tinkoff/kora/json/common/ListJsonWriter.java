package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonGenerator;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

public class ListJsonWriter<T> implements JsonWriter<List<T>> {
    private final JsonWriter<T> writer;

    public ListJsonWriter(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public void write(JsonGenerator gen, @Nullable List<T> object) throws IOException {
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
