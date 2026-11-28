package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class StringJsonReader implements JsonReader<String> {

    @Override
    public String read(JSONReader reader) {
        return reader.readString();
    }
}
