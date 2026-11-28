package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import org.jspecify.annotations.*;

import java.time.*;
import java.time.format.*;

public class LocalDateJsonReader implements JsonReader<LocalDate> {
    @Override
    public @Nullable LocalDate read(JSONReader reader) {
        return reader.readLocalDate();
    }
}
