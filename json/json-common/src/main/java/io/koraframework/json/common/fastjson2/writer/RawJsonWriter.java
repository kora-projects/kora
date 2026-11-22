package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.nio.charset.*;

public class RawJsonWriter implements JsonWriter<RawJson> {

    @Override
    public byte[] toByteArray(@Nullable RawJson object) {
        if (object == null) {
            return "null".getBytes(StandardCharsets.ISO_8859_1);
        }
        return object.value;
    }

    @Override
    public void write(JSONWriter writer, @Nullable RawJson object) {
        if (object == null) {
            writer.writeNull();
        } else {
            writer.writeRaw(object.value);
        }
    }
}
