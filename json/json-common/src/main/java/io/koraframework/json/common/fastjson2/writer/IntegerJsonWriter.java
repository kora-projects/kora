package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class IntegerJsonWriter implements JsonWriter<Integer> {

    @Override
    public void write(JSONWriter writer, Integer object) {
        writer.writeInt32(object);
    }
}
