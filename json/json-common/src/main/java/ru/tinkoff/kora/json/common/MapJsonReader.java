package ru.tinkoff.kora.json.common;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import java.util.LinkedHashMap;
import java.util.Map;

public class MapJsonReader<T> implements JsonReader<Map<String, T>> {
    private final JsonReader<T> reader;

    public MapJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public Map<String, T> read(JsonParser parser) {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token != JsonToken.START_OBJECT) {
            throw new StreamReadException(parser, "Expecting START_OBJECT token, got " + token);
        }
        token = parser.nextToken();
        if (token == JsonToken.END_OBJECT) {
            return Map.of();
        }

        Map<String, T> result = new LinkedHashMap<>();
        while (token != JsonToken.END_OBJECT) {
            if (token != JsonToken.PROPERTY_NAME) {
                throw new StreamReadException(parser, "Expecting PROPERTY_NAME token, got " + token);
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
