package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.JSONWriter;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;
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

    @Override
    public void write(JSONWriter writer, @Nullable Short object) {
        if (object == null) {
            writer.writeNull();
        } else {
            writer.writeInt16(object);
        }
    }
}
