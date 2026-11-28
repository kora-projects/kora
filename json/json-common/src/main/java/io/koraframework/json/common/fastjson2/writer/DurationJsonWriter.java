package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class DurationJsonWriter implements JsonWriter<Duration> {

    @Override
    public void write(JSONWriter writer, Duration object) {
        writer.writeString(object.toString());
    }
}
