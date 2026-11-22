package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

public class StringJsonWriter implements JsonWriter<String> {
    @Override
    public void write(JsonGenerator gen, String object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(object);
        }
    }
}
