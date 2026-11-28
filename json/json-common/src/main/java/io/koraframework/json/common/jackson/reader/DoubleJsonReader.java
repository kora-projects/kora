package io.koraframework.json.common.jackson.reader;

import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class DoubleJsonReader implements JsonReader<Double> {
    @Override
    public Double read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_NUMBER_FLOAT || t == JsonToken.VALUE_NUMBER_INT) {
            return parser.getDoubleValue();
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new StreamReadException(parser, "Failed to read json: expected a number, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }
}
