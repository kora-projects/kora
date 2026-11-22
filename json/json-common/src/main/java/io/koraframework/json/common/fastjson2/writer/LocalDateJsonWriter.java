package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class LocalDateJsonWriter implements JsonWriter<LocalDate> {

    @Override
    public void write(JSONWriter writer, LocalDate object) {
        writer.writeLocalDate(object);
    }
}
