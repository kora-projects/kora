package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class ZonedDateTimeJsonWriter implements JsonWriter<ZonedDateTime> {

    @Override
    public void write(JSONWriter writer, @Nullable ZonedDateTime object) {
        if (object == null) {
            writer.writeNull();
            return;
        }
        int year = object.getYear();
        int totalSeconds = object.getOffset().getTotalSeconds();
        if (year < 0 || year > 9999 || totalSeconds % 60 != 0) {
            writer.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_ZONED_DATE_TIME));
            return;
        }
        var zone = object.getZone();
        String zoneId = (zone instanceof ZoneOffset) ? null : zone.getId();
        int zoneExtraLen = (zoneId != null) ? (zoneId.length() + 2) : 0;
        int offsetLen = (totalSeconds == 0) ? 1 : 6;

        byte[] b = new byte[25 + offsetLen + zoneExtraLen];
        int i = 0;
        b[i] = '"';
        put4(b, 1, year);
        b[i + 5] = '-';
        put2(b, 6, object.getMonthValue());
        b[i + 8] = '-';
        put2(b, 9, object.getDayOfMonth());
        b[i + 11] = 'T';
        put2(b, 12, object.getHour());
        b[i + 14] = ':';
        put2(b, 15, object.getMinute());
        b[i + 17] = ':';
        put2(b, 18, object.getSecond());
        b[i + 20] = '.';
        put3(b, 21, object.getNano() / 1_000_000);

        int pos = 24;
        if (totalSeconds == 0) {
            b[pos++] = 'Z';
        } else {
            int absSeconds = totalSeconds < 0 ? -totalSeconds : totalSeconds;
            b[pos++] = (byte) (totalSeconds < 0 ? '-' : '+');
            put2(b, pos, absSeconds / 3600);
            b[pos + 2] = ':';
            put2(b, pos + 3, (absSeconds % 3600) / 60);
            pos += 5;
        }

        if (zoneId != null) {
            b[pos++] = '[';
            for (int j = 0, len = zoneId.length(); j < len; j++) {
                b[pos++] = (byte) zoneId.charAt(j);
            }
            b[pos++] = ']';
        }
        b[pos] = '"';
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
