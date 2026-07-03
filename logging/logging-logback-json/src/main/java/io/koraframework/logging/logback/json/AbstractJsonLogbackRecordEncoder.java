package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import io.koraframework.json.common.JsonModule;
import tools.jackson.core.JsonGenerator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class AbstractJsonLogbackRecordEncoder extends EncoderBase<ILoggingEvent> {

    private final List<LoggingEventJsonWriter> writers;

    protected AbstractJsonLogbackRecordEncoder(List<LoggingEventJsonWriter> writers) {
        this.writers = List.copyOf(writers);
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        try {
            return this.encode0(event);
        } catch (Exception e) {
            return this.encodeFailure(event, e);
        }
    }

    private byte[] encode0(ILoggingEvent event) throws IOException {
        var baos = new ByteArrayOutputStream(512);
        try (var gen = JsonModule.JSON_FACTORY.createGenerator(baos)) {
            gen.writeStartObject();
            for (var writer : this.writers) {
                writer.write(gen, event);
            }
            gen.writeEndObject();
        }
        baos.write('\n');
        return baos.toByteArray();
    }

    private byte[] encodeFailure(ILoggingEvent event, Exception exception) {
        try {
            var baos = new ByteArrayOutputStream(256);
            try (var gen = JsonModule.JSON_FACTORY.createGenerator(baos)) {
                gen.writeStartObject();
                gen.writeStringProperty("level", event.getLevel().levelStr);
                gen.writeStringProperty("logger", event.getLoggerName());
                gen.writeStringProperty("message", event.getFormattedMessage());
                gen.writeStringProperty("logging_encoder_error", exception.toString());
                gen.writeEndObject();
            }
            baos.write('\n');
            return baos.toByteArray();
        } catch (Exception e) {
            return "{\"logging_encoder_error\":\"failed\"}\n".getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public byte[] headerBytes() {
        return new byte[0];
    }

    @Override
    public byte[] footerBytes() {
        return new byte[0];
    }
}
