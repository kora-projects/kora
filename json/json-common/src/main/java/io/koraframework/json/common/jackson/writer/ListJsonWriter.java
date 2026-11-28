package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import org.jspecify.annotations.*;
import tools.jackson.core.*;

import java.util.*;

public class ListJsonWriter<T> implements JsonWriter<List<T>> {

    private static final byte[] EMPTY_ARRAY = new byte[] {'[', ']'};

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
