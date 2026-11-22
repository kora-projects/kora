package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class BooleanJsonWriter implements JsonWriter<Boolean> {

    @Override
    public void write(JSONWriter writer, Boolean object) {
        writer.writeBool(object);
    }
}
