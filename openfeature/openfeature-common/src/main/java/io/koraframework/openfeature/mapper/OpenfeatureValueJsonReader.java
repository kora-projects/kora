package io.koraframework.openfeature.mapper;

import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Value;
import io.koraframework.json.common.JsonReader;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Recursive Jackson 3 reader for all JSON-compatible OpenFeature values. */
public final class OpenfeatureValueJsonReader implements JsonReader<Value> {
    @Override
    public Value read(JsonParser parser) {
        var token = parser.currentToken();
        if (token == null) {
            throw new StreamReadException(parser, "Unexpected end of input for OpenFeature Value");
        }
        return switch (token) {
            case VALUE_NULL -> new Value();
            case VALUE_TRUE -> new Value(true);
            case VALUE_FALSE -> new Value(false);
            case VALUE_STRING -> new Value(parser.getString());
            case VALUE_NUMBER_INT -> new Value(parser.getLongValue());
            case VALUE_NUMBER_FLOAT -> new Value(parser.getDoubleValue());
            case START_ARRAY -> readArray(parser);
            case START_OBJECT -> readObject(parser);
            default -> throw new StreamReadException(parser, "Unexpected JSON token for OpenFeature Value: " + token);
        };
    }

    private Value readArray(JsonParser parser) {
        var values = new ArrayList<Value>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            values.add(read(parser));
        }
        return new Value(values);
    }

    private Value readObject(JsonParser parser) {
        var values = new LinkedHashMap<String, Value>();
        for (var token = parser.nextToken(); token != JsonToken.END_OBJECT; token = parser.nextToken()) {
            if (token != JsonToken.PROPERTY_NAME) {
                throw new StreamReadException(parser, "Expected a property name in OpenFeature Value");
            }
            var name = parser.currentName();
            parser.nextToken();
            values.put(name, read(parser));
        }
        return new Value(new ImmutableStructure(values));
    }
}
