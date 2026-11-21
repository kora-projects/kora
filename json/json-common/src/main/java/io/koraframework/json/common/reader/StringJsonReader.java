package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONException;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class StringJsonReader implements JsonReader<String> {
    @Override
    public String read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            return parser.getString();
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new StreamReadException(parser, "Failed to read json: expected a string, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }

    @Override
    public String read(JSONReader reader) {
        return reader.readString();
    }
}
