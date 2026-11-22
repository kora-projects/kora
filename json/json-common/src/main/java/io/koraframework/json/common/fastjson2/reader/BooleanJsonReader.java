package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class BooleanJsonReader implements JsonReader<Boolean> {

    @Override
    public Boolean read(JSONReader reader) {
        return reader.readBool();
    }
}
