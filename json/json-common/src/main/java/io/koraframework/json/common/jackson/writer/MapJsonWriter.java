package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import org.jspecify.annotations.*;
import tools.jackson.core.*;

import java.util.*;

public class MapJsonWriter<T> implements JsonWriter<Map<String, T>> {

    private static final byte[] EMPTY_OBJECT = new byte[] {'{', '}'};

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
