package io.koraframework.logging.logback.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Writes one part of a plain text record, see {@link io.koraframework.logging.logback.ConsoleTextRecordEncoder}.
 * <p>
 * A record is the concatenation of what every writer appends, in the order they were configured, so a writer owns the
 * separator that follows its own part and writes nothing when it has nothing to say.
 */
@FunctionalInterface
public interface LoggingEventTextWriter {

    void write(StringBuilder out, ILoggingEvent event);
}
