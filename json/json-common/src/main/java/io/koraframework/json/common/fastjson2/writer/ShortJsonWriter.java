package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

public class ShortJsonWriter implements JsonWriter<Short> {

    @Override
    public void write(JSONWriter writer, @Nullable Short object) {
        if (object == null) {
            writer.writeNull();
        } else {
            writer.writeInt16(object);
        }
    }
}
