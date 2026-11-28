package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

public class ShortJsonWriter implements JsonWriter<Short> {
    @Override
    public void write(JsonGenerator gen, Short object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(object);
        }
    }
}
