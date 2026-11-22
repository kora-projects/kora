package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class ByteArrayJsonReader implements JsonReader<byte[]> {

    @Override
    public byte[] read(JSONReader reader) {
        return reader.readBinary();
    }
}
