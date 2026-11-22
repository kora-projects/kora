package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class DoubleJsonReader implements JsonReader<Double> {

    @Override
    public Double read(JSONReader reader) {
        return reader.readDouble();
    }
}
