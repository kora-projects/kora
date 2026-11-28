package io.koraframework.json.common.fastjson2.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;

import java.time.*;

public class ZoneIdJsonReader implements JsonReader<ZoneId> {

    @Override
    public ZoneId read(JSONReader reader) {
        String str = reader.readString();
        if (str == null) {
            return null;
        }
        try {
            return ZoneId.of(str);
        } catch (DateTimeException e) {
            throw new JSONException(reader.info("Failed to read json: expected a valid ZoneId string"), e);
        }
    }
}
