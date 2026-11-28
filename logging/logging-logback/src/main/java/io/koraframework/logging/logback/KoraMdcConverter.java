package io.koraframework.logging.logback;

import ch.qos.logback.classic.pattern.*;
import ch.qos.logback.classic.spi.*;
import io.koraframework.common.context.*;
import io.koraframework.logging.common.*;
import io.koraframework.logging.common.arg.*;

import java.util.*;

public final class KoraMdcConverter extends ClassicConverter {
    @Override
    public String convert(ILoggingEvent event) {
        Map<String, StructuredArgumentWriter> mdc;
        if (event instanceof KoraLoggingEvent e) {
            mdc = e.koraMdc();
        } else {
            KoraContext currentContext = KoraContext.current();
            MDC currentMdc = currentContext != null ? currentContext.get(MDC.MDC_KEY) : null;
            mdc = currentMdc != null ? currentMdc.values() : Map.of();
        }

        if (mdc.isEmpty()) {
            return "";
        }
        var b = new StringBuilder();
        for (var entry : mdc.entrySet()) {
            b.append(entry.getKey()).append(": ").append(entry.getValue()).append(' ');
        }
        return b.toString();
    }
}
