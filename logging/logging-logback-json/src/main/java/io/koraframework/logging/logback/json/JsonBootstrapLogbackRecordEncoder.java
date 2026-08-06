package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.logback.json.writer.DefaultLoggingEventJsonWriter;
import io.koraframework.logging.logback.json.writer.DefaultExceptionJsonWriterLogging;
import io.koraframework.logging.logback.json.writer.DefaultStructuredJsonWriterLogging;
import io.koraframework.logging.logback.json.writer.DefaultTraceJsonWriterLogging;

import java.util.List;

public final class JsonBootstrapLogbackRecordEncoder extends AbstractJsonLogbackRecordEncoder {

    private volatile boolean disabled;

    public JsonBootstrapLogbackRecordEncoder() {
        super(List.of(
            new DefaultLoggingEventJsonWriter(),
            new DefaultExceptionJsonWriterLogging(),
            new DefaultStructuredJsonWriterLogging(),
            new DefaultTraceJsonWriterLogging()
        ));
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        if (this.disabled) {
            return new byte[0];
        }
        return super.encode(event);
    }

    public void disable() {
        this.disabled = true;
    }

    public void enable() {
        this.disabled = false;
    }
}
