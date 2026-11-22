package io.koraframework.json.common.jackson.reader;

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
}
