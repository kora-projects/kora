package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class ZoneIdJsonWriter implements JsonWriter<ZoneId> {

    @Override
    public void write(JSONWriter writer, ZoneId object) {
        if (object == null) {
            writer.writeNull();
        } else {
            writer.writeString(object.getId());
        }
    }
}
