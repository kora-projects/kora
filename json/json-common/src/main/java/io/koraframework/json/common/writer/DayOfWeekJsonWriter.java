package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.time.*;

public class DayOfWeekJsonWriter implements JsonWriter<DayOfWeek> {
    @Override
    public void write(JsonGenerator gen, DayOfWeek object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(object.name());
        }
    }

    @Override
    public void write(JSONWriter writer, DayOfWeek object) {
        writer.writeString(object.name());
    }
}
