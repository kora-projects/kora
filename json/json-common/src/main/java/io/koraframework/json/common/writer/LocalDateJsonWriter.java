package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.*;
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

    @Override
    public void write(JSONWriter writer, LocalDate object) {
        writer.writeLocalDate(object);
    }
}
