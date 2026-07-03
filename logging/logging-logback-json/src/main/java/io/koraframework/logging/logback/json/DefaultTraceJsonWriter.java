package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.logback.KoraLoggingEvent;
import io.opentelemetry.api.trace.SpanContext;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

public final class DefaultTraceJsonWriter implements LoggingEventJsonWriter {

    @Override
    public void write(JsonGenerator gen, ILoggingEvent event) throws IOException {
        if (event instanceof KoraLoggingEvent koraEvent) {
            var span = koraEvent.span();
            if (span != SpanContext.getInvalid()) {
                gen.writeStringProperty("traceId", span.getTraceId());
                gen.writeStringProperty("spanId", span.getSpanId());
            }
        }
    }
}
