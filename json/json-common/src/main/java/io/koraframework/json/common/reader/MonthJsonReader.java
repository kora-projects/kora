package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import java.time.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class MonthJsonReader implements JsonReader<Month> {
    @Override
    public Month read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        if (t == JsonToken.VALUE_STRING) {
            String valueAsString = parser.getValueAsString();
            for (Month month : KoraDateTimeFormatters.KORA_MONTHS) {
                if (month.name().equalsIgnoreCase(valueAsString)) {
                    return month;
                }
            }
            return Month.of(Integer.parseInt(valueAsString));
        }
        if (t == JsonToken.VALUE_NUMBER_INT) {
            return Month.of(parser.getValueAsInt());
        }
        throw new StreamReadException(parser, "Failed to read json: expected a string or integer number, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }

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
