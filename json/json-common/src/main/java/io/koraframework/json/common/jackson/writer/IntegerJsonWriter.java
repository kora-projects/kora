package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

public class IntegerJsonWriter implements JsonWriter<Integer> {
    @Override
    public void write(JsonGenerator gen, Integer object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(object);
        }
    }
}
