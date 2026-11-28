package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class MonthJsonWriter implements JsonWriter<Month> {

    @Override
    public void write(JSONWriter writer, Month object) {
        writer.writeString(object.name());
    }
}
