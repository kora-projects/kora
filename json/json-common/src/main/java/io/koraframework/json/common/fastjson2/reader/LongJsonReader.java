package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class LongJsonReader implements JsonReader<Long> {

    @Override
    public Long read(JSONReader reader) {
        return reader.readInt64();
    }
}
