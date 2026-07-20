package io.koraframework.logging.logback.json.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.logback.json.JsonFieldConstants;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

public final class DefaultLoggingEventJsonWriter implements LoggingEventJsonWriter {

    private static final CachingDateFormatter DATE_FORMATTER = new CachingDateFormatter();

    @Override
    public void write(JsonGenerator gen, ILoggingEvent event) throws IOException {
        gen.writeName(JsonFieldConstants.TIMESTAMP);
        gen.writeString(DATE_FORMATTER.format(event.getTimeStamp()));
        gen.writeName(JsonFieldConstants.LEVEL);
        gen.writeString(event.getLevel().levelStr);
        gen.writeName(JsonFieldConstants.THREAD);
        gen.writeString(event.getThreadName());
        gen.writeName(JsonFieldConstants.LOGGER);
        gen.writeString(event.getLoggerName());
        gen.writeName(JsonFieldConstants.MESSAGE);
        gen.writeString(event.getFormattedMessage());
    }
}
