package io.koraframework.logging.logback.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Writes the formatted message of a record.
 */
public final class DefaultMessageTextWriter implements LoggingEventTextWriter {

    @Override
    public void write(StringBuilder out, ILoggingEvent event) {
        out.append(event.getFormattedMessage());
    }
}
