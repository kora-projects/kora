package io.koraframework.logging.logback.json.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.logback.KoraLoggingEvent;
import io.koraframework.logging.logback.json.JsonFieldConstants;
import io.opentelemetry.api.trace.SpanContext;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

public final class DefaultTraceJsonWriterLogging implements LoggingEventJsonWriter {

    @Override
    public void write(JsonGenerator gen, ILoggingEvent event) throws IOException {
        if (event instanceof KoraLoggingEvent koraEvent) {
            var span = koraEvent.span();
            if (span != SpanContext.getInvalid()) {
                gen.writeName(JsonFieldConstants.TRACE_ID);
                gen.writeString(span.getTraceId());
                gen.writeName(JsonFieldConstants.SPAN_ID);
                gen.writeString(span.getSpanId());
            }
        }
    }
}
