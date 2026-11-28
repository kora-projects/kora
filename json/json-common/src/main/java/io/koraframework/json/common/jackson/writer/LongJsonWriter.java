package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

public class LongJsonWriter implements JsonWriter<Long> {
    @Override
    public void write(JsonGenerator gen, Long object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(object);
        }
    }
}
