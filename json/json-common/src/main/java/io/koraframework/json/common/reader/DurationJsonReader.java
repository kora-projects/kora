package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONException;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;

import java.time.Duration;
import java.time.format.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class DurationJsonReader implements JsonReader<Duration> {
    @Override
    public Duration read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            return Duration.parse(parser.getValueAsString());
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new StreamReadException(parser, "Failed to read json: expected a string, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }

    @Override
    public Duration read(JSONReader reader) {
        String str = reader.readString();
        if (str == null) {
            return null;
        }
        try {
            return Duration.parse(str);
        } catch (DateTimeParseException e) {
            throw new JSONException(reader.info("Failed to read json: expected an ISO-8601 duration string"), e);
        }
    }
}
