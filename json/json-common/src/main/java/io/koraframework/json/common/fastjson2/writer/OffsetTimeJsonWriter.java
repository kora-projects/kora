package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class OffsetTimeJsonWriter implements JsonWriter<OffsetTime> {

    @Override
    public void write(JSONWriter writer, @Nullable OffsetTime object) {
        if (object == null) {
            writer.writeNull();
            return;
        }
        int totalSeconds = object.getOffset().getTotalSeconds();
        if (totalSeconds % 60 != 0) {
            writer.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_OFFSET_TIME));
            return;
        }
        int i = 0;
        if (totalSeconds == 0) {
            byte[] b = new byte[14 + 1];
            writeTimePart(b, object);
            b[i + 13] = 'Z';
            b[i + 14] = '"';
            writer.writeRaw(b);
        } else {
            byte[] b = new byte[19 + 1];
            writeTimePart(b, object);
            int absSeconds = totalSeconds < 0 ? -totalSeconds : totalSeconds;
            b[i + 13] = (byte) (totalSeconds < 0 ? '-' : '+');
            int hours = absSeconds / 3600;
            int minutes = (absSeconds % 3600) / 60;
            put2(b, 14, hours);
            b[i + 16] = ':';
            put2(b, 17, minutes);
            b[i + 19] = '"';
            writer.writeRaw(b);
        }
    }

    private static void writeTimePart(byte[] b, OffsetTime time) {
        int i = 0;
        b[i] = '"';
        put2(b, 1, time.getHour());
        b[i + 3] = ':';
        put2(b, 4, time.getMinute());
        b[i + 6] = ':';
        put2(b, 7, time.getSecond());
        b[i + 9] = '.';
        put3(b, 10, time.getNano() / 1_000_000);
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
}
