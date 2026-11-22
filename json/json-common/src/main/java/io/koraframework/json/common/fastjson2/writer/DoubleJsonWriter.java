package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class DoubleJsonWriter implements JsonWriter<Double> {

    @Override
    public void write(JSONWriter writer, Double object) {
        writer.writeDouble(object);
    }
}
