package io.koraframework.logging.logback.json;

import io.koraframework.logging.logback.json.writer.LoggingEventJsonWriter;

import java.util.List;

public final class JsonReadyLogbackRecordEncoder extends AbstractJsonLogbackRecordEncoder {

    public JsonReadyLogbackRecordEncoder(List<LoggingEventJsonWriter> writers) {
        super(writers);
    }

    public JsonReadyLogbackRecordEncoder(List<LoggingEventJsonWriter> writers, LoggingEventJsonMasker masker) {
        super(writers, masker);
    }
}
