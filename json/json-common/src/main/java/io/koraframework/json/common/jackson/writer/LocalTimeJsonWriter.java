package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.time.*;

import static java.time.format.DateTimeFormatter.*;

public class LocalTimeJsonWriter implements JsonWriter<LocalTime> {
    @Override
    public void write(JsonGenerator gen, LocalTime object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_LOCAL_TIME));
        }
    }
}
