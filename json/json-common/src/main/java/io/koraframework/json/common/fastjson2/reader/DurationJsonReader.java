package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;
import java.time.format.*;

public class DurationJsonReader implements JsonReader<Duration> {

    @Override
    public Duration read(JSONReader reader) {
        String str = reader.readString();
        if (str == null) {
            return null;
        }
        try {
            return Duration.parse(str);
        } catch (DateTimeParseException e) {
            throw new JSONException(reader.info("Failed to read json: expected an ISO-8601 duration string"), e);
        }
    }
}
