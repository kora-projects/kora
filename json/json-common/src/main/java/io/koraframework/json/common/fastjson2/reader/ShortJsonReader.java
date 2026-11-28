package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class ShortJsonReader implements JsonReader<Short> {

    @Override
    public Short read(JSONReader reader) {
        return reader.readInt16();
    }
}
