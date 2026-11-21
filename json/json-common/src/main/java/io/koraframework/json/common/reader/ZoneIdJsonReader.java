package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import java.time.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class ZoneIdJsonReader implements JsonReader<ZoneId> {
    @Override
    public ZoneId read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            return ZoneId.of(parser.getValueAsString());
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new StreamReadException(parser, "Failed to read json: expected a string, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }

    @Override
    public ZoneId read(JSONReader reader) {
        String str = reader.readString();
        if (str == null) {
            return null;
        }
        try {
            return ZoneId.of(str);
        } catch (DateTimeException e) {
            throw new JSONException(reader.info("Failed to read json: expected a valid ZoneId string"), e);
        }
    }
}
