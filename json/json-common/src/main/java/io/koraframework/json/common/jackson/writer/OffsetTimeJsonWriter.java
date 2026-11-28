package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.time.*;

public class OffsetTimeJsonWriter implements JsonWriter<OffsetTime> {
    @Override
    public void write(JsonGenerator gen, OffsetTime object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_OFFSET_TIME));
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
