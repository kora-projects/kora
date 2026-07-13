package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import io.koraframework.json.common.JsonModule;
import io.koraframework.logging.logback.json.writer.LoggingEventJsonWriter;
import tools.jackson.core.JsonEncoding;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.util.ByteArrayBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public abstract class AbstractJsonLogbackRecordEncoder extends EncoderBase<ILoggingEvent> {

    public static final byte[] EMPTY = new byte[0];

    private final List<LoggingEventJsonWriter> writers;
    private final LoggingEventJsonMasker masker;

    protected AbstractJsonLogbackRecordEncoder(List<LoggingEventJsonWriter> writers) {
        this(writers, LoggingEventJsonMasker.noop());
    }

    protected AbstractJsonLogbackRecordEncoder(List<LoggingEventJsonWriter> writers, LoggingEventJsonMasker masker) {
        this.writers = List.copyOf(writers);
        this.masker = masker;
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        try {
            return this.encode0(event);
        } catch (Exception e) {
            return this.encodeWriteFailure(event, e);
        }
    }

    private byte[] encode0(ILoggingEvent event) throws IOException {
        var recycler = JsonModule.JSON_FACTORY._getBufferRecycler();
        var out = new ByteArrayBuilder(recycler, 256);
        try {
            try (var rawGen = JsonModule.JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), out, JsonEncoding.UTF8)) {
                var gen = this.masker == LoggingEventJsonMasker.noop()
                    ? rawGen
                    : new MaskingJsonGenerator(rawGen, this.masker);

                gen.writeStartObject();
                for (var writer : this.writers) {
                    writer.write(gen, event);
                }
                gen.writeEndObject();
            }
            out.append('\n');
            return out.toByteArray();
        } finally {
            out.release();
        }
    }

    private byte[] encodeWriteFailure(ILoggingEvent event, Exception exception) {
        var recycler = JsonModule.JSON_FACTORY._getBufferRecycler();
        var out = new ByteArrayBuilder(recycler, 512);
        try {
            try (var gen = JsonModule.JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), out, JsonEncoding.UTF8)) {
                gen.writeStartObject();
                gen.writeStringProperty("timestamp", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                gen.writeStringProperty("level", event.getLevel().levelStr);
                gen.writeStringProperty("logger", event.getLoggerName());
                gen.writeStringProperty("message", event.getFormattedMessage());
                gen.writeStringProperty("exception", exception.getMessage());
                gen.writeEndObject();
            }
            out.append('\n');
            return out.toByteArray();
        } catch (Exception e) {
            return ("{\"timestamp\":\"" + OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "\","
                + "\"level\":\"" + event.getLevel().levelStr
                + "\",\"logger\":\"" + event.getLoggerName()
                + "\",\"threadName\":\"" + event.getThreadName()
                + "\",\"exception\":\"" + exception.getMessage() + "\"}\n")
                .getBytes(StandardCharsets.UTF_8);
        } finally {
            out.release();
        }
    }

    @Override
    public byte[] headerBytes() {
        return EMPTY;
    }

    @Override
    public byte[] footerBytes() {
        return EMPTY;
    }
}
