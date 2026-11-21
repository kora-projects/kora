package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.time.*;
import java.time.format.*;

public class InstantJsonWriter implements JsonWriter<Instant> {
    @Override
    public void write(JsonGenerator gen, Instant object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(DateTimeFormatter.ISO_INSTANT.format(object));
        }
    }

    @Override
    public void write(JSONWriter writer, Instant object) {
        writer.writeInstant(object);
    }
}
