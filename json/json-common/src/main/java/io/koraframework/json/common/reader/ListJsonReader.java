package io.koraframework.json.common.reader;

import io.koraframework.json.common.JsonReader;
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
            throw new StreamReadException(parser, "Failed to read json array: expected an array, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
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

    private static String actualValue(JsonParser parser) {
        var token = parser.currentToken();
        if (token == null) {
            return "nothing (end of input)";
        }
        var value = parser.getValueAsString();
        if (value != null && value.length() > 128) {
            value = value.substring(0, 128) + "...(truncated)";
        }
        return switch (token) {
            case VALUE_NULL -> "null";
            case START_OBJECT -> "an object";
            case START_ARRAY -> "an array";
            case VALUE_STRING -> "a string \"" + value + "\"";
            case VALUE_NUMBER_INT -> "a number " + value;
            case VALUE_NUMBER_FLOAT -> "a fractional number " + value;
            case VALUE_TRUE, VALUE_FALSE -> "a boolean " + value;
            default -> "token " + token;
        };
    }

    private static String jsonPath(JsonParser parser) {
        var pointer = parser.streamReadContext().pathAsPointer().toString();
        return pointer.isEmpty() ? "<root>" : pointer;
    }
}
