package ru.tinkoff.kora.json.common;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import java.util.ArrayList;
import java.util.List;

public class ListJsonReader<T> implements JsonReader<List<T>> {
    private final JsonReader<T> reader;

    public ListJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public List<T> read(JsonParser parser) {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token != JsonToken.START_ARRAY) {
            throw new StreamReadException(parser, "Expecting START_ARRAY token, got " + token);
        }
        token = parser.nextToken();
        if (token == JsonToken.END_ARRAY) {
            return List.of();
        }

        List<T> result = new ArrayList<>();
        while (token != JsonToken.END_ARRAY) {
            var element = this.reader.read(parser);
            result.add(element);
            token = parser.nextToken();
        }

        return result;
    }
}
