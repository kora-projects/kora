package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class YearMonthJsonReader implements JsonReader<YearMonth> {

    @Override
    public YearMonth read(JSONReader reader) {
        String str = reader.readString();
        if (str == null) {
            return null;
        }
        if (str.length() == 7 && str.charAt(4) == '-') {
            int y0 = str.charAt(0) - '0';
            int y1 = str.charAt(1) - '0';
            int y2 = str.charAt(2) - '0';
            int y3 = str.charAt(3) - '0';
            int m0 = str.charAt(5) - '0';
            int m1 = str.charAt(6) - '0';
            if (y0 >= 0 && y0 <= 9 && y1 >= 0 && y1 <= 9 && y2 >= 0 && y2 <= 9 && y3 >= 0 && y3 <= 9
                && m0 >= 0 && m0 <= 9 && m1 >= 0 && m1 <= 9) {
                try {
                    return YearMonth.of(y0 * 1000 + y1 * 100 + y2 * 10 + y3, m0 * 10 + m1);
                } catch (DateTimeException e) {
                    throw new JSONException(reader.info("Failed to read json: expected a valid YearMonth"), e);
                }
            }
        }
        throw new JSONException(reader.info("Failed to read json: expected a string matching Kora YearMonth format"));
    }
}
