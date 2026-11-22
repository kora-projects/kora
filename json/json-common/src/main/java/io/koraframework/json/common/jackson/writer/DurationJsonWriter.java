package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.time.*;

public class DurationJsonWriter implements JsonWriter<Duration> {
    @Override
    public void write(JsonGenerator gen, Duration object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(object.toString());
        }
    }
}
