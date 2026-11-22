package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import io.koraframework.json.common.util.*;
import tools.jackson.core.*;

public class ObjectJsonWriter implements JsonWriter<Object> {
    @Override
    public void write(JsonGenerator gen, Object object) throws JacksonException {
        JsonObjectCodec.write(gen, object);
    }
}
