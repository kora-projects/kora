package io.koraframework.logging.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.common.MDC;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;

import java.util.Map;

public final class KoraMdcConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        var mdc = event instanceof KoraLoggingEvent e
            ? e.koraMdc()
            : MDC.VALUE.isBound() ? MDC.get().values() : Map.<String, StructuredArgumentWriter>of();
        if (mdc.isEmpty()) {
            return "";
        }
        var b = new StringBuilder();
        for (var entry : mdc.entrySet()) {
            b.append(entry.getKey()).append(": ").append(entry.getValue().writeToString()).append(' ');
        }
        return b.toString();
    }
}
