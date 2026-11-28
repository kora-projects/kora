package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.util.*;

public class SetJsonWriter<T> implements JsonWriter<Set<T>> {
    private final JsonWriter<T> writer;

    public SetJsonWriter(JsonWriter<T> writer) {
        this.writer = writer;
    }

    private static final byte[] EMPTY_ARRAY = new byte[] {'[', ']'};

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
