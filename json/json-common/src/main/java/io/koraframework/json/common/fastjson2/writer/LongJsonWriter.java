package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

public class LongJsonWriter implements JsonWriter<Long> {

    @Override
    public void write(JSONWriter writer, Long object) {
        writer.writeInt64(object);
    }
}
