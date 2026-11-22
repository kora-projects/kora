package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.time.*;

public class OffsetDateTimeJsonWriter implements JsonWriter<OffsetDateTime> {
    @Override
    public void write(JsonGenerator gen, OffsetDateTime object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_OFFSET_DATE_TIME));
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
