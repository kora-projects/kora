package ru.tinkoff.kora.logging.logback;

import ch.qos.logback.classic.pattern.Abbreviator;
import ch.qos.logback.classic.pattern.TargetLengthBasedClassNameAbbreviator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.status.Status;
import com.fasterxml.jackson.core.JsonFactory;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;
import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static java.time.ZoneOffset.UTC;

public final class ConsoleTextRecordEncoder implements Encoder<ILoggingEvent> {
    private final JsonFactory jsonFactory = new JsonFactory();
    private final CachingDateFormatter formatter = new CachingDateFormatter();
    private final Abbreviator abbreviator = new TargetLengthBasedClassNameAbbreviator(100);

    @Override
    public byte[] encode(ILoggingEvent event) {
        try {
            return this.encode0(event);
        } catch (IOException e) {
            return "<error>".getBytes(StandardCharsets.UTF_8);
        }
    }

    private byte[] encode0(ILoggingEvent event) throws IOException {
        var baos = new ByteArrayOutputStream(256);
        var w = new OutputStreamWriter(baos, StandardCharsets.UTF_8);

        w
            .append(this.formatter.format(event.getTimeStamp())).append(" ")
            .append("[").append(event.getThreadName()).append("] ")
            .append(event.getLevel().levelStr).append(" ")
            .append(this.abbreviator.abbreviate(event.getLoggerName()))
            .append(" ")
            .flush();
        if (event instanceof KoraLoggingEvent koraEvent) {
            var mdc = koraEvent.koraMdc();
            for (var e : mdc.entrySet()) {
                var key = e.getKey();
                var value = e.getValue();
                w.append(key).append("=").flush();
                this.writeJson(baos, value);
                w.append(" ");
            }
            w.flush();
        }
        for (var e : event.getMDCPropertyMap().entrySet()) {
            var key = e.getKey();
            var value = e.getValue();
            w.append(key).append("=").append(value).flush();
            w.append(" ");
        }

        w.append(event.getFormattedMessage()).flush();
        if (event.getMarkerList() != null) for (var marker : event.getMarkerList()) {
            if (marker instanceof StructuredArgument structuredArgument) {
                w.append("\n")
                    .append("\t").append(structuredArgument.fieldName()).append("=")
                    .flush();
                this.writeJson(baos, structuredArgument);
            }
        }
        if (event.getArgumentArray() != null) for (var arg : event.getArgumentArray()) {
            if (arg instanceof StructuredArgument structuredArgument) {
                w.append("\n")
                    .append("\t").append(structuredArgument.fieldName()).append("=")
                    .flush();
                this.writeJson(baos, structuredArgument);
            }
        }
        if (event.getKeyValuePairs() != null) for (var keyValue : event.getKeyValuePairs()) {
            if (keyValue.value instanceof StructuredArgumentWriter structuredArgument) {
                w.append("\n")
                    .append("\t").append(keyValue.key).append("=")
                    .flush();
                this.writeJson(baos, structuredArgument);
            }
        }
        w.append("\n");
        if (event.getThrowableProxy() != null) {
            w.append(ThrowableProxyUtil.asString(event.getThrowableProxy()));
            w.append("\n");
        }
        w.flush();
        return baos.toByteArray();
    }

    private void writeJson(ByteArrayOutputStream b, StructuredArgumentWriter value) {
        try (var gen = this.jsonFactory.createGenerator(b)) {
            value.writeTo(gen);
        } catch (IOException e) {
            try {
                b.write("<error>".getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
            }
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

    @Override
    public void setContext(Context context) {

    }

    @Override
    public Context getContext() {
        return null;
    }

    @Override
    public void addStatus(Status status) {

    }

    @Override
    public void addInfo(String msg) {

    }

    @Override
    public void addInfo(String msg, Throwable ex) {

    }

    @Override
    public void addWarn(String msg) {

    }

    @Override
    public void addWarn(String msg, Throwable ex) {

    }

    @Override
    public void addError(String msg) {

    }

    @Override
    public void addError(String msg, Throwable ex) {

    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public boolean isStarted() {
        return true;
    }

    public static class CachingDateFormatter {
        private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        private long lastTimestamp = -1;
        private String cachedStr = null;

        public final String format(long now) {
            if (now != this.lastTimestamp) {
                this.lastTimestamp = now;
                this.cachedStr = this.formatter.format(Instant.ofEpochMilli(now).atZone(UTC));
            }
            return this.cachedStr;
        }
    }
}
