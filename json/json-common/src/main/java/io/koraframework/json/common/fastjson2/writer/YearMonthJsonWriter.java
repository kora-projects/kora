package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class YearMonthJsonWriter implements JsonWriter<YearMonth> {

    @Override
    public void write(JSONWriter writer, @Nullable YearMonth object) {
        if (object == null) {
            writer.writeNull();
            return;
        }
        int year = object.getYear();
        if (year < 0 || year > 9999) {
            writer.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_YEAR_MONTH));
            return;
        }
        int i = 0;
        byte[] b = new byte[8 + 1];
        b[i] = '"';
        put4(b, 1, year);
        b[i + 5] = '-';
        put2(b, 6, object.getMonthValue());
        b[i + 8] = '"';
        writer.writeRaw(b);
    }

    private static void put4(byte[] b, int off, int v) {
        put2(b, off, v / 100);
        put2(b, off + 2, v % 100);
    }

    private static void put2(byte[] b, int off, int v) {
        b[off] = (byte) ('0' + v / 10);
        b[off + 1] = (byte) ('0' + v % 10);
    }
}
