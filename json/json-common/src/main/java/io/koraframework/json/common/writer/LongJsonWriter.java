package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.JSONWriter;
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

    @Override
    public void write(JSONWriter writer, Long object) {
        writer.writeInt64(object);
    }
}
