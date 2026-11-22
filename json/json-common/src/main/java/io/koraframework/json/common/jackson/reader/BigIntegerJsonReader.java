package io.koraframework.json.common.jackson.reader;

import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import java.math.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class BigIntegerJsonReader implements JsonReader<BigInteger> {
    @Override
    public BigInteger read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_NUMBER_INT) {
            return parser.getBigIntegerValue();
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new StreamReadException(parser, "Failed to read json: expected an integer number, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }
}
