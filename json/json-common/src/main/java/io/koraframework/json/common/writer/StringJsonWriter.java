package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.JSONWriter;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;
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

    @Override
    public void write(JSONWriter writer, @Nullable String object) {
        writer.writeString(object);
    }
}
