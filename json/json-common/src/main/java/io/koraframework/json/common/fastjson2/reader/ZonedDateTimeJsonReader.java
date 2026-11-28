package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class ZonedDateTimeJsonReader implements JsonReader<ZonedDateTime> {

    @Override
    public @Nullable ZonedDateTime read(JSONReader reader) {
        return reader.readZonedDateTime();
    }
}
