package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class MonthJsonReader implements JsonReader<Month> {

    @Override
    public Month read(JSONReader reader) {
        if (reader.isInt()) {
            try {
                return Month.of(reader.readInt32Value());
            } catch (DateTimeException e) {
                throw new JSONException(reader.info("Failed to read json: expected a valid month number (1-12)"), e);
            }
        }
        String valueAsString = reader.readString();
        if (valueAsString == null) {
            return null;
        }
        int len = valueAsString.length();
        if (len == 1) {
            char c = valueAsString.charAt(0);
            if (c >= '1' && c <= '9') {
                return Month.of(c - '0');
            }
        } else if (len == 2 && valueAsString.charAt(0) == '1') {
            char c2 = valueAsString.charAt(1);
            if (c2 >= '0' && c2 <= '2') {
                return Month.of(10 + (c2 - '0'));
            }
        }
        for (Month month : KoraDateTimeFormatters.KORA_MONTHS) {
            if (month.name().equalsIgnoreCase(valueAsString)) {
                return month;
            }
        }
        throw new JSONException(reader.info("Failed to read json: expected a string or integer number"));
    }
}
