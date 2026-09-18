package io.koraframework.logging.logback.json.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.logback.CachingTimestampFormatter;
import io.koraframework.logging.logback.KoraLogbackProperties;
import io.koraframework.logging.logback.json.JsonFieldConstants;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

public final class DefaultLoggingEventJsonWriter implements LoggingEventJsonWriter {

    private static final CachingTimestampFormatter DATE_FORMATTER =
        new CachingTimestampFormatter(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

    private static final boolean EPOCH_MILLIS = KoraLogbackProperties.isTimestampEpochMillis(warning -> { });

    @Override
    public void write(JsonGenerator gen, ILoggingEvent event) throws IOException {
        writeTimestamp(gen, event.getTimeStamp());
        gen.writeName(JsonFieldConstants.LEVEL);
        gen.writeString(event.getLevel().levelStr);
        gen.writeName(JsonFieldConstants.THREAD);
        gen.writeString(event.getThreadName());
        gen.writeName(JsonFieldConstants.LOGGER);
        gen.writeString(event.getLoggerName());
        gen.writeName(JsonFieldConstants.MESSAGE);
        gen.writeString(event.getFormattedMessage());
    }

    /**
     * Writes the timestamp field, as a number of epoch milliseconds or as an ISO 8601 string depending on
     * {@link KoraLogbackProperties#TIMESTAMP_EPOCH_MILLIS_PROPERTY}, so every record, including a fallback one, keeps the
     * same JSON type for it: a log index maps a field once, and a field that is a number in one record and a string in
     * another gets records rejected.
     */
    public static void writeTimestamp(JsonGenerator gen, long timestamp) {
        gen.writeName(JsonFieldConstants.TIMESTAMP);
        if (EPOCH_MILLIS) {
            gen.writeNumber(timestamp);
        } else {
            gen.writeString(DATE_FORMATTER.format(timestamp));
        }
    }

    /**
     * @return the timestamp as a JSON value, a number of epoch milliseconds or a quoted ISO 8601 string
     */
    public static String timestampJsonValue(long timestamp) {
        return EPOCH_MILLIS ? Long.toString(timestamp) : '"' + DATE_FORMATTER.format(timestamp) + '"';
    }
}
