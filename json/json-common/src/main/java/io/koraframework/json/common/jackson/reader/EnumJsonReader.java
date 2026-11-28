package io.koraframework.json.common.jackson.reader;

import io.koraframework.json.common.*;
import org.jspecify.annotations.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

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
    public T read(JsonParser parser) throws JacksonException {
        V jsonValue = this.valueReader.read(parser);
        if (jsonValue == null) {
            return null;
        }
        T value = this.values.get(jsonValue);
        if (value == null) {
            throw new StreamReadException(parser, "Failed to read json enum: expected one of " + this.values.keySet() + ", but got \"" + jsonValue + "\" (at " + jsonPath(parser) + ")");
        }
        return value;
    }

    @Nullable

    private static String jsonPath(JsonParser parser) {
        var context = parser.streamReadContext();
        if (context == null) {
            return "<root>";
        }
        String pointer = context.pathAsPointer().toString();
        return pointer.isEmpty() ? "<root>" : pointer;
    }
}
