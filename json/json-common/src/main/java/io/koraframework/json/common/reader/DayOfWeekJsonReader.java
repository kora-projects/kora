package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import java.time.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class DayOfWeekJsonReader implements JsonReader<DayOfWeek> {
    @Override
    public DayOfWeek read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        if (t == JsonToken.VALUE_STRING) {
            String valueAsString = parser.getValueAsString();
            for (DayOfWeek dayOfWeek : KoraDateTimeFormatters.KORA_DAY_OF_WEEKS) {
                if (dayOfWeek.name().equalsIgnoreCase(valueAsString)) {
                    return dayOfWeek;
                }
            }
            return DayOfWeek.of(Integer.parseInt(valueAsString));
        }
        if (t == JsonToken.VALUE_NUMBER_INT) {
            return DayOfWeek.of(parser.getValueAsInt());
        }
        throw new StreamReadException(parser, "Failed to read json: expected a string or integer number, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }

    @Override
    public DayOfWeek read(JSONReader reader) {
        if (reader.isInt()) {
            return DayOfWeek.of(reader.readInt32Value());
        }
        String valueAsString = reader.readString();
        if (valueAsString == null) {
            return null;
        }
        if (valueAsString.length() == 1) {
            char c = valueAsString.charAt(0);
            if (c >= '1' && c <= '7') {
                return DayOfWeek.of(c - '0');
            }
        }
        for (DayOfWeek dayOfWeek : KoraDateTimeFormatters.KORA_DAY_OF_WEEKS) {
            if (dayOfWeek.name().equalsIgnoreCase(valueAsString)) {
                return dayOfWeek;
            }
        }
        throw new JSONException(reader.info("Failed to read json: expected a string or integer number"));
    }
}
