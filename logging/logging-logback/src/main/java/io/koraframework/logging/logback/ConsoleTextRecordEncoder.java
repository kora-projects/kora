package io.koraframework.logging.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;
import io.koraframework.logging.logback.writer.DefaultExceptionTextWriter;
import io.koraframework.logging.logback.writer.DefaultLoggingEventTextWriter;
import io.koraframework.logging.logback.writer.DefaultMdcTextWriter;
import io.koraframework.logging.logback.writer.DefaultMessageTextWriter;
import io.koraframework.logging.logback.writer.DefaultStructuredTextWriter;
import io.koraframework.logging.logback.writer.DefaultTraceTextWriter;
import io.koraframework.logging.logback.writer.LoggingEventTextWriter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Encodes logging events as plain text, one record per line followed by its structured arguments and stack trace.
 * <p>
 * A record is assembled from {@link LoggingEventTextWriter} parts, each appending its own piece in order, so a custom
 * layout is a list of writers rather than a new encoder. It can be declared in a Logback configuration file, where
 * writers are nested elements:
 * <pre>{@code
 * <encoder class="io.koraframework.logging.logback.ConsoleTextRecordEncoder">
 *     <writer class="io.koraframework.logging.logback.writer.DefaultLoggingEventTextWriter"/>
 *     <writer class="io.koraframework.logging.logback.writer.DefaultMessageTextWriter"/>
 * </encoder>
 * }</pre>
 * When no writer is declared, {@link #defaultWriters(boolean)} are used.
 */
public final class ConsoleTextRecordEncoder extends EncoderBase<ILoggingEvent> {

    private static final byte[] EMPTY = new byte[0];

    private final List<LoggingEventTextWriter> writers = new ArrayList<>();
    private boolean writersConfigured = false;

    public ConsoleTextRecordEncoder() {
        this(false);
    }

    /**
     * @param colored whether the default layout highlights the timestamp and the level with ANSI escape codes, the
     *                same way {@code %cyan(%d) %highlight(%-5level)} does, meant for tests and local runs
     */
    public ConsoleTextRecordEncoder(boolean colored) {
        this.writers.addAll(defaultWriters(colored));
    }

    public ConsoleTextRecordEncoder(List<LoggingEventTextWriter> writers) {
        this.writers.addAll(writers);
        this.writersConfigured = true;
    }

    public static List<LoggingEventTextWriter> defaultWriters(boolean colored) {
        return List.of(
            new DefaultLoggingEventTextWriter(colored),
            new DefaultTraceTextWriter(),
            new DefaultMdcTextWriter(),
            new DefaultMessageTextWriter(),
            new DefaultStructuredTextWriter(),
            new DefaultExceptionTextWriter()
        );
    }

    /**
     * Adds a writer, replacing {@link #defaultWriters(boolean)} on first call, see {@code <writer class="..."/>} in a Logback
     * configuration file, which is why it returns nothing: Logback only recognizes an {@code add} method returning
     * {@code void}.
     */
    public void addWriter(LoggingEventTextWriter writer) {
        if (!this.writersConfigured) {
            this.writers.clear();
            this.writersConfigured = true;
        }
        this.writers.add(writer);
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        var out = new StringBuilder(256);
        try {
            for (var writer : this.writers) {
                writer.write(out, event);
            }
        } catch (RuntimeException e) {
            // a writer may come from application code, and a broken one must not lose the record
            out.setLength(0);
            out.append(event.getLevel().levelStr).append(' ')
                .append(event.getLoggerName()).append(" - ")
                .append(event.getFormattedMessage())
                .append(" <text encoding failed: ").append(e).append('>');
        }
        return out.append('\n').toString().getBytes(StandardCharsets.UTF_8);
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
