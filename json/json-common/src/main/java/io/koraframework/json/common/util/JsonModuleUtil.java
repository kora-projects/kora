package io.koraframework.json.common.util;

import tools.jackson.core.*;

public final class JsonModuleUtil {

    private JsonModuleUtil() {
    }

    public static String actualValue(JsonParser parser) {
        JsonToken token = parser.currentToken();
        if (token == null) {
            return "nothing (end of input)";
        }
        String value = parser.getValueAsString();
        if (value != null && value.length() > 128) {
            value = value.substring(0, 128) + "...(truncated)";
        }
        if (token == JsonToken.VALUE_NULL) return "null";
        if (token == JsonToken.START_OBJECT) return "an object";
        if (token == JsonToken.START_ARRAY) return "an array";
        if (token == JsonToken.VALUE_STRING) return "a string \"" + value + "\"";
        if (token == JsonToken.VALUE_NUMBER_INT) return "a number " + value;
        if (token == JsonToken.VALUE_NUMBER_FLOAT) return "a fractional number " + value;
        if (token == JsonToken.VALUE_TRUE || token == JsonToken.VALUE_FALSE) return "a boolean " + value;
        return "token " + token;
    }

    public static String jsonPath(JsonParser parser) {
        var context = parser.streamReadContext();
        if (context == null) {
            return "<root>";
        }
        String pointer = context.pathAsPointer().toString();
        return pointer.isEmpty() ? "<root>" : pointer;
    }
}
