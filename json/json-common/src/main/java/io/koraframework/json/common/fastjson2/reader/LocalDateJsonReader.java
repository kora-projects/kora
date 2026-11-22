package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;
import java.time.format.*;

public class LocalDateJsonReader implements JsonReader<LocalDate> {

    @Override
    public LocalDate read(JSONReader reader) {
        String str = reader.readString();
        if (str == null) {
            return null;
        }
        try {
            return LocalDate.parse(str, KoraDateTimeFormatters.KORA_ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new JSONException(reader.info("Failed to read json: expected a string"), e);
        }
    }
}
