package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

import static java.time.format.DateTimeFormatter.*;

public class LocalTimeJsonWriter implements JsonWriter<LocalTime> {

    @Override
    public void write(JSONWriter writer, LocalTime object) {
        writer.writeString(object.format(ISO_LOCAL_TIME));
    }
}
