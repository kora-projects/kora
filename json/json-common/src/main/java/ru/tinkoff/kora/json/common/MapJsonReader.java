package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class MapJsonReader<T> implements JsonReader<Map<String, T>> {
    private final JsonReader<T> reader;

    public MapJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public Map<String, T> read(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token != JsonToken.START_OBJECT) {
            throw new JsonParseException(parser, "Expecting START_OBJECT token, got " + token);
        }
        token = parser.nextToken();
        if (token == JsonToken.END_OBJECT) {
            return Map.of();
        }
        var result = new HashMap<String, T>();
        while (token != JsonToken.END_OBJECT) {
            if (token != JsonToken.FIELD_NAME) {
                throw new JsonParseException(parser, "Expecting FIELD_NAME token, got " + token);
            }
            var fieldName = parser.currentName();
            token = parser.nextToken();
            var element = this.reader.read(parser);
            result.put(fieldName, element);
            token = parser.nextToken();
        }

        return result;
    }
}
