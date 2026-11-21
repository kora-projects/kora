package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.JSONWriter;
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

    @Override
    public void write(JSONWriter writer, Double object) {
        writer.writeDouble(object);
    }
}
