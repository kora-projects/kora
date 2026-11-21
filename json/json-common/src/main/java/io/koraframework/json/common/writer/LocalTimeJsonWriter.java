package io.koraframework.json.common.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;

import java.time.*;

import static java.time.format.DateTimeFormatter.*;

public class LocalTimeJsonWriter implements JsonWriter<LocalTime> {
    @Override
    public void write(JsonGenerator gen, LocalTime object) throws JacksonException {
        if (object == null) {
            gen.writeNull();
        } else {
            gen.writeString(object.format(ISO_LOCAL_TIME));
        }
    }

    @Override
    public void write(JSONWriter writer, LocalTime object) {
        writer.writeString(object.format(ISO_LOCAL_TIME));
    }
}
