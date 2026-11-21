package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.JSONWriter;
import io.koraframework.json.common.JsonWriter;
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

    private static final byte[] EMPTY_ARRAY = new byte[]{'[', ']'};

    @Override
    public void write(JSONWriter writer, @Nullable Set<T> object) {
        if (object == null) {
            writer.writeNull();
            return;
        }
        if (object.isEmpty()) {
            writer.writeRaw(EMPTY_ARRAY);
            return;
        }
        writer.startArray();
        for (var element : object) {
            this.writer.write(writer, element);
        }
        writer.endArray();
    }
}
