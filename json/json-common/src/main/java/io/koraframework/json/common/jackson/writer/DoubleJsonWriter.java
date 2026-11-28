package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

public class DoubleJsonWriter implements JsonWriter<Double> {
    @Override
    public void write(JsonGenerator gen, Double object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(object);
        }
    }
}
