package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import io.koraframework.json.common.util.*;
import tools.jackson.core.*;

public class ObjectJsonWriter implements JsonWriter<Object> {
    @Override
    public void write(JsonGenerator gen, Object object) throws JacksonException {
        JsonObjectCodec.write(gen, object);
    }

    @Override
    public void write(JSONWriter writer, Object object) {
        writer.writeAny(object);
    }
}
