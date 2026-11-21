package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONException;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class ShortJsonReader implements JsonReader<Short> {
    @Override
    public Short read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_NUMBER_INT) {
            return parser.getShortValue();
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new StreamReadException(parser, "Failed to read json: expected an integer number, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }

    @Override
    public Short read(JSONReader reader) {
        return reader.readInt16();
    }
}
