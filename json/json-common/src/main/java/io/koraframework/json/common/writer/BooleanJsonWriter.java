package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;

public class BooleanJsonWriter implements JsonWriter<Boolean> {
    @Override
    public void write(JsonGenerator gen, Boolean object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeBoolean(object);
        }
    }

    @Override
    public void write(JSONWriter writer, Boolean object) {
        writer.writeBool(object);
    }
}
