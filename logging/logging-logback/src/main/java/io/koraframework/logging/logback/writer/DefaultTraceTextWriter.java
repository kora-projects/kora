package io.koraframework.logging.logback.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.logback.KoraLoggingEvent;
import io.opentelemetry.api.trace.SpanContext;

/**
 * Writes the trace and span ids of a record logged inside a span, and nothing otherwise.
 */
public final class DefaultTraceTextWriter implements LoggingEventTextWriter {

    @Override
    public void write(StringBuilder out, ILoggingEvent event) {
        if (event instanceof KoraLoggingEvent koraEvent && koraEvent.span() != SpanContext.getInvalid()) {
            out.append("traceId=").append(koraEvent.span().getTraceId()).append(' ');
            out.append("spanId=").append(koraEvent.span().getSpanId()).append(' ');
        }
    }
}
