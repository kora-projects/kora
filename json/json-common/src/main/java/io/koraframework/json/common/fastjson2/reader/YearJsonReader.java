package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class YearJsonReader implements JsonReader<Year> {

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
    }
}
