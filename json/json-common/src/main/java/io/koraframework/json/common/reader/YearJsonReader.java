package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import java.time.*;
import java.time.format.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class YearJsonReader implements JsonReader<Year> {
    @Override
    public Year read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            return Year.parse(parser.getValueAsString(), KoraDateTimeFormatters.KORA_ISO_YEAR);
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new StreamReadException(parser, "Failed to read json: expected a string, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }

    @Override
    public Year read(JSONReader reader) {
        String str = reader.readString();
        if (str == null) {
            return null;
        }
        if (str.length() == 4) {
            int d0 = str.charAt(0) - '0';
            int d1 = str.charAt(1) - '0';
            int d2 = str.charAt(2) - '0';
            int d3 = str.charAt(3) - '0';
            if (d0 >= 0 && d0 <= 9 && d1 >= 0 && d1 <= 9 && d2 >= 0 && d2 <= 9 && d3 >= 0 && d3 <= 9) {
                return Year.of(d0 * 1000 + d1 * 100 + d2 * 10 + d3);
            }
        }
        throw new JSONException(reader.info("Failed to read json: expected a string matching Kora Year format"));
    }}
