package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class MonthDayJsonReader implements JsonReader<MonthDay> {

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
