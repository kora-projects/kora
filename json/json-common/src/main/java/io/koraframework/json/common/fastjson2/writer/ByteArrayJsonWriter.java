package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class ByteArrayJsonWriter implements JsonWriter<byte[]> {

    @Override
    public void write(JSONWriter writer, byte[] object) {
        writer.writeBinary(object);
    }
}
