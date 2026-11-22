package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class DayOfWeekJsonReader implements JsonReader<DayOfWeek> {

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
