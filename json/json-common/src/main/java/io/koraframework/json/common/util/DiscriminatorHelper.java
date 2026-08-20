package io.koraframework.json.common.util;

import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.core.io.SerializedString;

public class DiscriminatorHelper {
    @Nullable
    public static String readStringDiscriminator(BufferingJsonParser parser, String fieldName) {
        var token = parser.currentToken();
        var name = new SerializedString(fieldName);
        if (token != JsonToken.START_OBJECT) {
            throw new StreamReadException(parser, "Failed to read json: expected an object to read discriminator field \"" + fieldName + "\", but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
        }
        while (!parser.nextName(name)) {
            if (parser.currentToken() == JsonToken.END_OBJECT) {
                return null;
            }
            parser.skipChildren();
        }
        if (parser.nextToken() != JsonToken.VALUE_STRING) {
            throw new StreamReadException(parser, "Failed to read json: expected a string discriminator value for field \"" + fieldName + "\", but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
        }
        return parser.getValueAsString();
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
