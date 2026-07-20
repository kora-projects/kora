package io.koraframework.logging.logback.json;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JacksonException;

public interface LoggingEventJsonMasker {

    boolean shouldMask(String path, String fieldName);

    default void writeMasked(String path, String fieldName, JsonGenerator gen) throws JacksonException {
        gen.writeString("***");
    }

    static LoggingEventJsonMasker noop() {
        return NoopLoggingEventJsonMasker.INSTANCE;
    }
}
