package io.koraframework.logging.logback.json;

import java.util.List;

public final class JsonLogbackRecordEncoder extends AbstractJsonLogbackRecordEncoder {

    public JsonLogbackRecordEncoder(List<LoggingEventJsonWriter> writers) {
        super(writers);
    }
}
