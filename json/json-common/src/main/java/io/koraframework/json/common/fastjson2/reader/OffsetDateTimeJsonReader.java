package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class OffsetDateTimeJsonReader implements JsonReader<OffsetDateTime> {
    @Override
    public @Nullable OffsetDateTime read(JSONReader reader) {
        return reader.readOffsetDateTime();
    }
}
