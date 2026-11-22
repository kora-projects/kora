package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class InstantJsonReader implements JsonReader<Instant> {

    @Override
    public Instant read(JSONReader reader) {
        return reader.readInstant();
    }
}
