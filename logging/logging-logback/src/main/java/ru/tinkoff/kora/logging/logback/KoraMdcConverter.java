package ru.tinkoff.kora.logging.logback;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ru.tinkoff.kora.logging.common.MDC;

public final class KoraMdcConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        var mdc = event instanceof KoraLoggingEvent e
            ? e.koraMdc()
            : MDC.get().values();
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
