package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class ObjectJsonWriter implements JsonWriter<Object> {

    @Override
    public void write(JSONWriter writer, Object object) {
        writer.writeAny(object);
    }
}
