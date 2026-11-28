package io.koraframework.json.common.fastjson2.writer;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class LocalTimeJsonWriter implements JsonWriter<LocalTime> {

    @Override
    public void write(JSONWriter writer, @Nullable LocalTime object) {
        if (object == null) {
            writer.writeNull();
        } else {
            writer.writeString(object.format(KoraDateTimeFormatters.KORA_ISO_LOCAL_TIME));
        }
    }
}
