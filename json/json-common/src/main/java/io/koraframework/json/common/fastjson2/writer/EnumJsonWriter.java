package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.util.function.*;

public final class EnumJsonWriter<T extends Enum<T>, V> implements JsonWriter<T> {
    private final RawJson[] values;

    public EnumJsonWriter(T[] values, Function<T, V> valueExtractor, JsonWriter<V> valueWriter) {
        this.values = new RawJson[values.length];
        for (int i = 0; i < values.length; i++) {
            var enumValue = values[i];
            var value = valueExtractor.apply(enumValue);
            var bytes = valueWriter.toByteArray(value);
            this.values[i] = new RawJson(bytes);
        }
    }

    @Override
    public void write(JSONWriter writer, @Nullable T object) {
        if (object == null) {
            writer.writeNull();
            return;
        }
        writer.writeRaw(this.values[object.ordinal()].value);
    }
}
