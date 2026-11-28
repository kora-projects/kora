package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.spi.*;
import ch.qos.logback.core.encoder.*;
import io.koraframework.json.common.util.*;
import io.koraframework.logging.logback.json.writer.*;
import tools.jackson.core.*;
import tools.jackson.core.util.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;

/**
 * Encodes logging events as a single line of JSON.
 * <p>
 * Can be declared in a Logback configuration file, where writers and masking are configured as nested elements:
 * <pre>{@code
 * <encoder class="io.koraframework.logging.logback.json.JsonRecordEncoder">
 *     <writer class="com.example.MyJsonWriter"/>
 *     <maskField>password</maskField>
 * </encoder>
 * }</pre>
 * When no writer is declared, {@link #defaultWriters()} are used.
 */
public class JsonRecordEncoder extends EncoderBase<ILoggingEvent> {

    private static final byte[] EMPTY = new byte[0];

    private final List<LoggingEventJsonWriter> writers = new ArrayList<>();
    private final Set<String> maskFields = new LinkedHashSet<>();
    private LoggingEventJsonMasker masker = LoggingEventJsonMasker.noop();
    private boolean writersConfigured = false;

    public JsonRecordEncoder() {
        this.writers.addAll(defaultWriters());
    }

    public JsonRecordEncoder(List<LoggingEventJsonWriter> writers) {
        this(writers, LoggingEventJsonMasker.noop());
    }

    public JsonRecordEncoder(List<LoggingEventJsonWriter> writers, LoggingEventJsonMasker masker) {
        this.writers.addAll(writers);
        this.masker = masker;
        this.writersConfigured = true;
    }

    public static List<LoggingEventJsonWriter> defaultWriters() {
        return List.of(
            new DefaultLoggingEventJsonWriter(),
            new DefaultTraceJsonWriterLogging(),
            new DefaultMdcJsonWriterLogging(),
            new DefaultStructuredJsonWriterLogging(),
            new DefaultExceptionJsonWriterLogging()
        );
    }

    /**
     * Adds a writer, replacing {@link #defaultWriters()} on first call, see {@code <writer class="..."/>} in a Logback
     * configuration file, which is why it returns nothing: Logback only recognizes an {@code add} method returning
     * {@code void}.
     */
    public void addWriter(LoggingEventJsonWriter writer) {
        if (!this.writersConfigured) {
            this.writers.clear();
            this.writersConfigured = true;
        }
        this.writers.add(writer);
    }

    /**
     * See {@code <masker class="..."/>} in a Logback configuration file.
     */
    public void setMasker(LoggingEventJsonMasker masker) {
        this.masker = masker;
    }

    /**
     * Masks values of the given field, see {@code <maskField>password</maskField>} in a Logback configuration file.
     */
    public void addMaskField(String field) {
        this.maskFields.add(field);
    }

    @Override
    public void start() {
        if (!this.maskFields.isEmpty()) {
            if (this.masker == LoggingEventJsonMasker.noop()) {
                this.masker = new FieldLoggingEventJsonMasker(this.maskFields);
            } else {
                this.addWarn("Both <masker> and <maskField> are configured, <maskField> values are ignored");
            }
        }
        super.start();
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
        var recycler = BufferingJsonParser.JSON_FACTORY._getBufferRecycler();
        var out = new ByteArrayBuilder(recycler, 256);
        try {
            try (var rawGen = BufferingJsonParser.JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), out, JsonEncoding.UTF8)) {
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
        var recycler = BufferingJsonParser.JSON_FACTORY._getBufferRecycler();
        var out = new ByteArrayBuilder(recycler, 512);
        try {
            try (var gen = BufferingJsonParser.JSON_FACTORY.createGenerator(ObjectWriteContext.empty(), out, JsonEncoding.UTF8)) {
                gen.writeStartObject();
                DefaultLoggingEventJsonWriter.writeTimestamp(gen, event.getTimeStamp());
                gen.writeStringProperty("level", event.getLevel().levelStr);
                gen.writeStringProperty("logger", event.getLoggerName());
                gen.writeStringProperty("message", event.getFormattedMessage());
                gen.writeStringProperty("exception", exception.getMessage());
                gen.writeEndObject();
            }
            out.append('\n');
            return out.toByteArray();
        } catch (Exception e) {
            return ("{\"" + JsonFieldConstants.TIMESTAMP.getValue() + "\":" + DefaultLoggingEventJsonWriter.timestampJsonValue(event.getTimeStamp()) + ","
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
