package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ListJsonReader<T> implements JsonReader<List<T>> {
    private final JsonReader<T> reader;

    public ListJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public List<T> read(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token != JsonToken.START_ARRAY) {
            throw new JsonParseException(parser, "Expecting START_ARRAY token, got " + token);
        }
        token = parser.nextToken();
        if (token == JsonToken.END_ARRAY) {
            return List.of();
        }

        List<T> result = null;
        while (token != JsonToken.END_ARRAY) {
            var element = this.reader.read(parser);
            token = parser.nextToken();

            if (result == null) {
                if (token == JsonToken.END_ARRAY) {
                    return List.of(element);
                } else {
                    result = new ArrayList<>(8);
                }
            }
            result.add(element);
        }

        return result;
    }
}
