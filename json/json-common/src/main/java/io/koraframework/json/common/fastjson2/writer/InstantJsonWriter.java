package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class InstantJsonWriter implements JsonWriter<Instant> {

    @Override
    public void write(JSONWriter writer, Instant object) {
        writer.writeInstant(object);
    }
}
