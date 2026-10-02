package io.koraframework.openfeature;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Value;
import jakarta.annotation.Nullable;
import io.koraframework.json.common.JsonReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Рекурсивная десериализация JSON в OpenFeature {@link Value}.
 * Парсер позиционируется вызывающим на токене значения (контракт Kora JSON).
 */
public final class OpenfeatureValueJsonReader implements JsonReader<Value> {

    @Override
    @Nullable
    public Value read(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == null) {
            return null;
        }
        return switch (token) {
            case VALUE_NULL -> new Value();
            case VALUE_TRUE -> new Value(Boolean.TRUE);
            case VALUE_FALSE -> new Value(Boolean.FALSE);
            case VALUE_STRING -> new Value(parser.getValueAsString());
            case VALUE_NUMBER_INT -> new Value(parser.getIntValue());
            case VALUE_NUMBER_FLOAT -> new Value(parser.getDoubleValue());
            case START_ARRAY -> readArray(parser);
            case START_OBJECT -> readObject(parser);
            default -> throw new IOException("Unexpected JSON token for Value: " + token);
        };
    }

    private Value readArray(JsonParser parser) throws IOException {
        List<Value> list = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            list.add(read(parser));
        }
        return new Value(list);
    }

    private Value readObject(JsonParser parser) throws IOException {
        Map<String, Value> map = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.currentName();
            parser.nextToken();
            map.put(name, read(parser));
        }
        return new Value(new ImmutableStructure(map));
    }
}
