package io.koraframework.json.common.reader;

import com.alibaba.fastjson2.*;
import io.koraframework.json.common.*;
import tools.jackson.core.*;
import tools.jackson.core.exc.*;

import java.time.*;
import java.time.format.*;

import static io.koraframework.json.common.util.JsonModuleUtil.*;

public class ZonedDateTimeJsonReader implements JsonReader<ZonedDateTime> {
    @Override
    public ZonedDateTime read(JsonParser parser) throws JacksonException {
        JsonToken t = parser.currentToken();
        if (t == JsonToken.VALUE_STRING) {
            return ZonedDateTime.parse(parser.getValueAsString(), KoraDateTimeFormatters.KORA_ISO_ZONED_DATE_TIME);
        }
        if (t == JsonToken.VALUE_NULL) {
            return null;
        }
        throw new StreamReadException(parser, "Failed to read json: expected a string, but got " + actualValue(parser) + " (at " + jsonPath(parser) + ")");
    }

    @Override
    public ZonedDateTime read(JSONReader reader) {
        String str = reader.readString();
        if (str == null) {
            return null;
        }
        try {
            return ZonedDateTime.parse(str, KoraDateTimeFormatters.KORA_ISO_ZONED_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new JSONException(reader.info("Failed to read json: expected a string matching Kora ZonedDateTime format"), e);
        }
    }
}
