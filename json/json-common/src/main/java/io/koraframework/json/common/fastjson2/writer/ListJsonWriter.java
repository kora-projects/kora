package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.util.*;

public class ListJsonWriter<T> implements JsonWriter<List<T>> {

    private static final byte[] EMPTY_ARRAY = new byte[] {'[', ']'};

    private final JsonWriter<T> writer;

    public ListJsonWriter(JsonWriter<T> writer) {
        this.writer = writer;
    }

    @Override
    public void write(JSONWriter writer, @Nullable List<T> object) {
        if (object == null) {
            writer.writeNull();
            return;
        }
        int size = object.size();
        if (size == 0) {
            writer.writeRaw(EMPTY_ARRAY);
            return;
        }
        writer.startArray();
        if (object instanceof RandomAccess) {
            for (int i = 0; i < size; i++) {
                this.writer.write(writer, object.get(i));
            }
        } else {
            for (var element : object) {
                this.writer.write(writer, element);
            }
        }
        writer.endArray();
    }
}
