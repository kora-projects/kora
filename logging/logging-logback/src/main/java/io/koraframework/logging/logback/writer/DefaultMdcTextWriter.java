package io.koraframework.logging.logback.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.logback.KoraLoggingEvent;

/**
 * Writes the Kora MDC with values as JSON, then the SLF4J MDC with values as they are, each entry as {@code key=value}
 * followed by a space.
 */
public final class DefaultMdcTextWriter implements LoggingEventTextWriter {

    @Override
    public void write(StringBuilder out, ILoggingEvent event) {
        if (event instanceof KoraLoggingEvent koraEvent) {
            for (var entry : koraEvent.koraMdc().entrySet()) {
                out.append(entry.getKey()).append('=').append(entry.getValue().writeToString()).append(' ');
            }
        }
        for (var entry : event.getMDCPropertyMap().entrySet()) {
            out.append(entry.getKey()).append('=').append(entry.getValue()).append(' ');
        }
    }
}
