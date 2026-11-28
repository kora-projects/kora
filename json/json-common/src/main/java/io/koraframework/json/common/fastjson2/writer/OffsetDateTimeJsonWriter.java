package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class OffsetDateTimeJsonWriter implements JsonWriter<OffsetDateTime> {

    @Override
    public void write(JSONWriter writer, @Nullable OffsetDateTime object) {
        if (object == null) {
            writer.writeNull();
            return;
        }
        int year = object.getYear();
        int totalSeconds = object.getOffset().getTotalSeconds();
        if (year < 0 || year > 9999 || totalSeconds % 60 != 0) {
            writer.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_OFFSET_DATE_TIME));
            return;
        }
        if (totalSeconds == 0) {
            byte[] b = new byte[25 + 1];
            writeDateTimePart(b, year, object);
            b[24] = 'Z';
            b[25] = '"';
            writer.writeRaw(b);
        } else {
            byte[] b = new byte[30 + 1];
            writeDateTimePart(b, year, object);
            int absSeconds = totalSeconds < 0 ? -totalSeconds : totalSeconds;
            b[24] = (byte) (totalSeconds < 0 ? '-' : '+');
            int hours = absSeconds / 3600;
            int minutes = (absSeconds % 3600) / 60;
            put2(b, 25, hours);
            b[27] = ':';
            put2(b, 28, minutes);
            b[30] = '"';
            writer.writeRaw(b);
        }
    }

    private static void writeDateTimePart(byte[] b, int year, OffsetDateTime dt) {
        int i = 0;
        b[i] = '"';
        put4(b, 1, year);
        b[i + 5] = '-';
        put2(b, 6, dt.getMonthValue());
        b[i + 8] = '-';
        put2(b, 9, dt.getDayOfMonth());
        b[i + 11] = 'T';
        put2(b, 12, dt.getHour());
        b[i + 14] = ':';
        put2(b, 15, dt.getMinute());
        b[i + 17] = ':';
        put2(b, 18, dt.getSecond());
        b[i + 20] = '.';
        put3(b, 21, dt.getNano() / 1_000_000);
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
