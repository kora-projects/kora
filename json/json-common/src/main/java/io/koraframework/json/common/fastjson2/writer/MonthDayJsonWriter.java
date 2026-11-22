package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class MonthDayJsonWriter implements JsonWriter<MonthDay> {

    @Override
    public void write(JSONWriter writer, @Nullable MonthDay object) {
        if (object == null) {
            writer.writeNull();
            return;
        }
        int m = object.getMonthValue();
        int d = object.getDayOfMonth();
        var b = new byte[7];
        b[0] = '"';
        b[1] = (byte) ('0' + m / 10);
        b[2] = (byte) ('0' + m % 10);
        b[3] = '-';
        b[4] = (byte) ('0' + d / 10);
        b[5] = (byte) ('0' + d % 10);
        b[6] = '"';
        writer.writeRaw(b);
    }
}
