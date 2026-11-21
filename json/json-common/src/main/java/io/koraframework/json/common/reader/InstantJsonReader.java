package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONException;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.StreamReadException;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class InstantJsonReader implements JsonReader<Instant> {
    @Override
    public Instant read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            return DateTimeFormatter.ISO_INSTANT.parse(parser.getValueAsString()).query(Instant::from);
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new StreamReadException(parser, "Failed to read json: expected a string, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }

    @Override
    public Instant read(JSONReader reader) {
        return reader.readInstant();
    }
}
