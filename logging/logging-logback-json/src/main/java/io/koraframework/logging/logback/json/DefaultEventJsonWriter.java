package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.spi.ILoggingEvent;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;
import java.time.Instant;

public final class DefaultEventJsonWriter implements LoggingEventJsonWriter {

    @Override
    public void write(JsonGenerator gen, ILoggingEvent event) throws IOException {
        gen.writeStringProperty("timestamp", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        gen.writeStringProperty("level", event.getLevel().levelStr);
        gen.writeStringProperty("thread", event.getThreadName());
        gen.writeStringProperty("logger", event.getLoggerName());
        gen.writeStringProperty("message", event.getFormattedMessage());
    }
}
