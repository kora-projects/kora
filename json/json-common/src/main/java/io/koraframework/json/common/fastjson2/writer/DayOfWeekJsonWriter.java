package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class DayOfWeekJsonWriter implements JsonWriter<DayOfWeek> {

    @Override
    public void write(JSONWriter writer, DayOfWeek object) {
        writer.writeString(object.name());
    }
}
