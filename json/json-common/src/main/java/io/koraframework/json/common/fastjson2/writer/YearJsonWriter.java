package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class YearJsonWriter implements JsonWriter<Year> {

    @Override
    public void write(JSONWriter writer, @Nullable Year object) {
        if (object == null) {
            writer.writeNull();
            return;
        }
        int year = object.getValue();
        if (year < 0 || year > 9999) {
            writer.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_YEAR));
            return;
        }
        int i = 0;
        byte[] b = new byte[5 + 1];
        b[i] = '"';
        put4(b, 1, year);
        b[i + 5] = '"';
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
