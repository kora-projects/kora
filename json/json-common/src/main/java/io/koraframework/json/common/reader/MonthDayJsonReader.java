package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import java.time.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class MonthDayJsonReader implements JsonReader<MonthDay> {
    @Override
    public MonthDay read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            return MonthDay.parse(parser.getValueAsString(), KoraDateTimeFormatters.KORA_ISO_MONTH_DAY);
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new StreamReadException(parser, "Failed to read json: expected a string, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }

    @Override
    public MonthDay read(JSONReader reader) {
        String str = reader.readString();
        if (str == null) {
            return null;
        }
        if (str.length() == 5 && str.charAt(2) == '-') {
            int m1 = str.charAt(0) - '0';
            int m2 = str.charAt(1) - '0';
            int d1 = str.charAt(3) - '0';
            int d2 = str.charAt(4) - '0';
            if (m1 >= 0 && m1 <= 9 && m2 >= 0 && m2 <= 9 && d1 >= 0 && d1 <= 9 && d2 >= 0 && d2 <= 9) {
                try {
                    return MonthDay.of(m1 * 10 + m2, d1 * 10 + d2);
                } catch (DateTimeException e) {
                    throw new JSONException(reader.info("Failed to read json: expected a valid MonthDay"), e);
                }
            }
        }
        throw new JSONException(reader.info("Failed to read json: expected a string matching MM-dd"));
    }
}
