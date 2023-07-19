package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class EnumJsonReader<T extends Enum<T>> implements JsonReader<T> {
    private final Map<String, T> values;

    public EnumJsonReader(T[] values, Function<T, String> mapper) {
        this.values = new HashMap<>();
        for (var value : values) {
            this.values.put(mapper.apply(value), value);
        }
    }

    @Nullable
    @Override
    public T read(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token != JsonToken.VALUE_STRING) {
            throw new JsonParseException(parser, "Expecting VALUE_STRING token, got " + token);
        }
        var stringValue = parser.getText();
        var value = this.values.get(stringValue);
        if (value == null) {
            throw new JsonParseException(parser, "Expecting one of " + this.values.keySet() + ", got " + stringValue);
        }
        return value;
    }
}
