package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.time.*;
import java.time.format.*;

public class LocalDateJsonWriter implements JsonWriter<LocalDate> {
    @Override
    public void write(JsonGenerator gen, LocalDate object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(object.format(DateTimeFormatter.ISO_DATE));
        }
    }
}
