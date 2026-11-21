package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.JSONWriter;
import io.koraframework.json.common.*;
import tools.jackson.core.*;

public class FloatJsonWriter implements JsonWriter<Float> {
    @Override
    public void write(JsonGenerator gen, Float object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(object);
        }
    }

    @Override
    public void write(JSONWriter writer, Float object) {
        writer.writeFloat(object);
    }
}
