package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;

public class OffsetTimeJsonReader implements JsonReader<OffsetTime> {
    @Override
    public @Nullable OffsetTime read(JSONReader reader) {
        return reader.readOffsetTime();
    }
}
