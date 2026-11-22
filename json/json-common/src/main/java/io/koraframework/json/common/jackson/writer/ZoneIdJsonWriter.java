package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.time.*;

public class ZoneIdJsonWriter implements JsonWriter<ZoneId> {
    @Override
    public void write(JsonGenerator gen, ZoneId object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(object.getId());
        }
    }
}
