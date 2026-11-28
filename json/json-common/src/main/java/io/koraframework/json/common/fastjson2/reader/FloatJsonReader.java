package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class FloatJsonReader implements JsonReader<Float> {

    @Override
    public Float read(JSONReader reader) {
        return reader.readFloat();
    }
}
