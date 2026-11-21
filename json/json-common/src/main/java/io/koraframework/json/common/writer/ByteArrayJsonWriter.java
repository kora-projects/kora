package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.*;
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

    @Override
    public void write(JSONWriter writer, byte[] object) {
        writer.writeBinary(object);
    }
}
