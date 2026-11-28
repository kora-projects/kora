package io.koraframework.json.common.jackson.writer;

import io.koraframework.json.common.*;
import tools.jackson.core.*;

public class ByteArrayJsonWriter implements JsonWriter<byte[]> {
    @Override
    public void write(JsonGenerator gen, byte[] object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeBinary(object);
        }
    }
}
