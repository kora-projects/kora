package io.koraframework.logging.logback.json.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.logback.json.JsonFieldConstants;
import io.koraframework.logging.logback.writer.CachingTimestampFormatter;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

public final class DefaultLoggingEventJsonWriter implements LoggingEventJsonWriter {

    private static final CachingTimestampFormatter DATE_FORMATTER =
        new CachingTimestampFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

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
