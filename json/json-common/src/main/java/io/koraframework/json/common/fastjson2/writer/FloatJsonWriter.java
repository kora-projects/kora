package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class FloatJsonWriter implements JsonWriter<Float> {

    @Override
    public void write(JSONWriter writer, Float object) {
        writer.writeFloat(object);
    }
}
