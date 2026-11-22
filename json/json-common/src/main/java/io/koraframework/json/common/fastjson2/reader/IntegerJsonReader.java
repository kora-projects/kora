package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class IntegerJsonReader implements JsonReader<Integer> {

    @Override
    public Integer read(JSONReader reader) {
        return reader.readInt32();
    }
}
