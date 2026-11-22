package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;
import java.time.format.*;

public class OffsetDateTimeJsonReader implements JsonReader<OffsetDateTime> {

    @Override
    public OffsetDateTime read(JSONReader reader) {
        String str = reader.readString();
        if (str == null) {
            return null;
        }
        try {
            return OffsetDateTime.parse(str, KoraDateTimeFormatters.KORA_ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new JSONException(reader.info("Failed to read json: expected a string matching Kora OffsetDateTime format"), e);
        }
    }
}
