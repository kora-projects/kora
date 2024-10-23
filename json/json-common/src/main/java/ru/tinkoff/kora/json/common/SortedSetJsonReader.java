package ru.tinkoff.kora.json.common;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.util.SortedSet;
import java.util.TreeSet;

public class SortedSetJsonReader<T extends Comparable<T>> implements JsonReader<SortedSet<T>> {
    private final JsonReader<T> reader;

    public SortedSetJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public SortedSet<T> read(JsonParser parser) throws IOException {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token != JsonToken.START_ARRAY) {
            throw new JsonParseException(parser, "Expecting START_ARRAY token, got " + token);
        }
        token = parser.nextToken();
        if (token == JsonToken.END_ARRAY) {
            return new TreeSet<>();
        }
        var result = new TreeSet<T>();
        while (token != JsonToken.END_ARRAY) {
            var element = this.reader.read(parser);
            result.add(element);
            token = parser.nextToken();
        }

        return result;
    }
}
