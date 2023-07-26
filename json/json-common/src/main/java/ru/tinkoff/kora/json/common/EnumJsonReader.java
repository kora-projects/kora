package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class EnumJsonReader<T extends Enum<T>, V> implements JsonReader<T> {
    private final Map<V, T> values;
    private final JsonReader<V> valueReader;

    public EnumJsonReader(T[] values, Function<T, V> mapper, JsonReader<V> valueReader) {
        this.values = new HashMap<>();
        for (var value : values) {
            this.values.put(mapper.apply(value), value);
        }
        this.valueReader = valueReader;
    }

    @Nullable
    @Override
    public T read(JsonParser parser) throws IOException {
        var jsonValue = this.valueReader.read(parser);
        if (jsonValue == null) {
            return null;
        }
        var value = this.values.get(jsonValue);
        if (value == null) {
            throw new JsonParseException(parser, "Expecting one of " + this.values.keySet() + ", got " + jsonValue);
        }
        return value;
    }
}
