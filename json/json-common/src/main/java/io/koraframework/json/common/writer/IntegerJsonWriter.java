package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.JSONWriter;
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

    @Override
    public void write(JSONWriter writer, Integer object) {
        writer.writeInt32(object);
    }
}
