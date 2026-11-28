package io.koraframework.json.common.jackson.reader;

import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import java.util.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class SortedSetJsonReader<T extends Comparable<T>> implements JsonReader<SortedSet<T>> {
    private final JsonReader<T> reader;

    public SortedSetJsonReader(JsonReader<T> reader) {
        this.reader = reader;
    }

    @Override
    public SortedSet<T> read(JsonParser parser) {
        var token = parser.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token != JsonToken.START_ARRAY) {
            throw new StreamReadException(parser, "Failed to read json array: expected an array, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
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
