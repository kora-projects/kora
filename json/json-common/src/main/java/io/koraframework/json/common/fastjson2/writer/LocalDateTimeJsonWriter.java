package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class LocalDateTimeJsonWriter implements JsonWriter<LocalDateTime> {

    @Override
    public void write(JSONWriter writer, @Nullable LocalDateTime object) {
        if (object == null) {
            writer.writeNull();
            return;
        }
        int year = object.getYear();
        if (year < 0 || year > 9999) {
            writer.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_LOCAL_DATE_TIME));
            return;
        }
        byte[] b = new byte[25];
        b[0] = '"';
        put4(b, 1, year);
        b[5] = '-';
        put2(b, 6, object.getMonthValue());
        b[8] = '-';
        put2(b, 9, object.getDayOfMonth());
        b[11] = 'T';
        put2(b, 12, object.getHour());
        b[14] = ':';
        put2(b, 15, object.getMinute());
        b[17] = ':';
        put2(b, 18, object.getSecond());
        b[20] = '.';
        put3(b, 21, object.getNano() / 1_000_000);
        b[24] = '"';
        writer.writeRaw(b);
    }

    private static void put2(byte[] b, int off, int v) {
        b[off] = (byte) ('0' + v / 10);
        b[off + 1] = (byte) ('0' + v % 10);
    }

    private static void put3(byte[] b, int off, int v) {
        b[off] = (byte) ('0' + v / 100);
        b[off + 1] = (byte) ('0' + (v / 10) % 10);
        b[off + 2] = (byte) ('0' + v % 10);
    }

    private static void put4(byte[] b, int off, int v) {
        put2(b, off, v / 100);
        put2(b, off + 2, v % 100);
    }
}
