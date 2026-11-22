package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class ObjectJsonReader implements JsonReader<Object> {

    @Override
    public Object read(JSONReader reader) {
        return reader.readAny();
    }
}
