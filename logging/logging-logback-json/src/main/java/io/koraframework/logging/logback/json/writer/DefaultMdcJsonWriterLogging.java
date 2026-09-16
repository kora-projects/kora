package io.koraframework.logging.logback.json.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import io.koraframework.logging.logback.KoraLoggingEvent;
import io.koraframework.logging.logback.json.JsonFieldConstants;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes the {@code mdc} object of a record, merging the Kora MDC with the SLF4J one, where the Kora MDC wins on
 * conflicting keys.
 */
public final class DefaultMdcJsonWriterLogging implements LoggingEventJsonWriter {

    private static final Map<String, SerializedString> MDC_KEY_CACHE = new ConcurrentHashMap<>();

    @Override
    public void write(JsonGenerator gen, ILoggingEvent event) throws IOException {
        var koraMdc = this.koraMdc(event);
        var slf4jMdc = event.getMDCPropertyMap();
        if (koraMdc.isEmpty() && slf4jMdc.isEmpty()) {
            return;
        }

        gen.writeName(JsonFieldConstants.MDC);
        gen.writeStartObject();
        for (var entry : koraMdc.entrySet()) {
            gen.writeName(getMdcKey(entry.getKey()));
            entry.getValue().writeTo(gen);
        }
        for (var entry : slf4jMdc.entrySet()) {
            if (!koraMdc.containsKey(entry.getKey())) {
                gen.writeName(getMdcKey(entry.getKey()));
                gen.writeString(entry.getValue());
            }
        }
        gen.writeEndObject();
    }

    private Map<String, StructuredArgumentWriter> koraMdc(ILoggingEvent event) {
        if (event instanceof KoraLoggingEvent koraEvent) {
            return koraEvent.koraMdc();
        }
        return Map.of();
    }

    private static SerializedString getMdcKey(String key) {
        return MDC_KEY_CACHE.computeIfAbsent(key, SerializedString::new);
    }
}
