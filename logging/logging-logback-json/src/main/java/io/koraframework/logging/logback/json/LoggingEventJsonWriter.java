package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.spi.ILoggingEvent;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

public interface LoggingEventJsonWriter {

    void write(JsonGenerator gen, ILoggingEvent event) throws IOException;
}
