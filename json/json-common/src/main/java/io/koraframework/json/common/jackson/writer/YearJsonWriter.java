package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.time.*;

public class YearJsonWriter implements JsonWriter<Year> {
    @Override
    public void write(JsonGenerator gen, Year object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_YEAR));
        }
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
