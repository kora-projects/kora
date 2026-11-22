package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

public class StringJsonWriter implements JsonWriter<String> {

    @Override
    public void write(JSONWriter writer, @Nullable String object) {
        writer.writeString(object);
    }
}
