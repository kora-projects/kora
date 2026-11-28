package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;
import tools.jackson.core.*;

import java.util.*;
import java.util.function.*;

public final class EnumJsonReader<T extends Enum<T>, V> implements JsonReader<T> {
    private final Map<V, T> values;
    private final JsonReader<V> valueReader;

    @SuppressWarnings("unchecked")
    public EnumJsonReader(T[] values, Function<T, V> mapper, JsonReader<V> valueReader) {
        int len = values.length;
        Map.Entry<V, T>[] entries = new Map.Entry[len];
        for (int i = 0; i < len; i++) {
            entries[i] = Map.entry(mapper.apply(values[i]), values[i]);
        }
        this.values = Map.ofEntries(entries);
        this.valueReader = valueReader;
    }

    @Nullable
    @Override
    public T read(JSONReader reader) {
        V jsonValue = this.valueReader.read(reader);
        if (jsonValue == null) {
            return null;
        }
        T value = this.values.get(jsonValue);
        if (value == null) {
            throw new JSONException(reader.info("Failed to read json enum: expected one of " + this.values.keySet() + ", but got \"" + jsonValue + "\""));
        }
        return value;
    }
}
