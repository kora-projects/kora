package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SetJsonReader<T> implements JsonReader<Set<T>> {
    private final JsonReader<T> reader;

    public SetJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public Set<T> read(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token != JsonToken.START_ARRAY) {
            throw new JsonParseException(parser, "Expecting START_ARRAY token, got " + token);
        }
        token = parser.nextToken();
        if (token == JsonToken.END_ARRAY) {
            return Set.of();
        }
        var result = new HashSet<T>();
        while (token != JsonToken.END_ARRAY) {
            var element = this.reader.read(parser);
            result.add(element);
            token = parser.nextToken();
        }

        return result;
    }
}
