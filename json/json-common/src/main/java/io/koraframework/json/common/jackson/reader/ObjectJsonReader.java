package io.koraframework.json.common.jackson.reader;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

public class ObjectJsonReader implements JsonReader<Object> {
    @Override
    public Object read(JsonParser parser) throws JacksonException {
        return io.koraframework.json.common.util.JsonObjectCodec.parse(parser);
    }
}
