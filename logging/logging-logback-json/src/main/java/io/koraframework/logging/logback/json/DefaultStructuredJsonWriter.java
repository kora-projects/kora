package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import io.koraframework.logging.logback.KoraLoggingEvent;
import org.slf4j.event.KeyValuePair;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DefaultStructuredJsonWriter implements LoggingEventJsonWriter {

    @Override
    public void write(JsonGenerator gen, ILoggingEvent event) throws IOException {
        this.writeMdc(gen, event);
        this.writeStructuredArguments(gen, event);
    }

    private void writeMdc(JsonGenerator gen, ILoggingEvent event) throws IOException {
        var koraMdc = this.koraMdc(event);
        var slf4jMdc = event.getMDCPropertyMap();
        if (koraMdc.isEmpty() && slf4jMdc.isEmpty()) {
            return;
        }

        gen.writeName("mdc");
        gen.writeStartObject();
        for (var entry : koraMdc.entrySet()) {
            gen.writeName(entry.getKey());
            entry.getValue().writeTo(gen);
        }
        for (var entry : slf4jMdc.entrySet()) {
            if (!koraMdc.containsKey(entry.getKey())) {
                gen.writeStringProperty(entry.getKey(), entry.getValue());
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

    private void writeStructuredArguments(JsonGenerator gen, ILoggingEvent event) throws IOException {
        var structuredArguments = new ArrayList<StructuredArgument>();
        this.addStructuredArguments(structuredArguments, event);

        var data = this.findData(structuredArguments);
        if (data != null) {
            gen.writeName("data");
            data.writeTo(gen);
        }

        if (this.hasAttributes(structuredArguments, event.getKeyValuePairs())) {
            gen.writeName("attributes");
            gen.writeStartObject();
            for (var argument : structuredArguments) {
                if (argument != data) {
                    gen.writeName(argument.fieldName());
                    argument.writeTo(gen);
                }
            }
            var keyValuePairs = event.getKeyValuePairs();
            if (keyValuePairs != null) {
                for (var keyValuePair : keyValuePairs) {
                    this.writeKeyValuePair(gen, keyValuePair);
                }
            }
            gen.writeEndObject();
        }
    }

    private void addStructuredArguments(List<StructuredArgument> result, ILoggingEvent event) {
        var markers = event.getMarkerList();
        if (markers != null) {
            for (var marker : markers) {
                if (marker instanceof StructuredArgument structuredArgument) {
                    result.add(structuredArgument);
                }
            }
        }

        var arguments = event.getArgumentArray();
        if (arguments != null) {
            for (var argument : arguments) {
                if (argument instanceof StructuredArgument structuredArgument) {
                    result.add(structuredArgument);
                }
            }
        }
    }

    private StructuredArgument findData(List<StructuredArgument> structuredArguments) {
        for (var argument : structuredArguments) {
            if ("data".equals(argument.fieldName())) {
                return argument;
            }
        }
        return null;
    }

    private boolean hasAttributes(List<StructuredArgument> structuredArguments, List<KeyValuePair> keyValuePairs) {
        for (var argument : structuredArguments) {
            if (!"data".equals(argument.fieldName())) {
                return true;
            }
        }
        return keyValuePairs != null && !keyValuePairs.isEmpty();
    }

    private void writeKeyValuePair(JsonGenerator gen, KeyValuePair keyValuePair) throws IOException {
        gen.writeName(keyValuePair.key);
        if (keyValuePair.value instanceof StructuredArgumentWriter structuredArgumentWriter) {
            structuredArgumentWriter.writeTo(gen);
        } else if (keyValuePair.value == null) {
            gen.writeNull();
        } else {
            gen.writeString(keyValuePair.value.toString());
        }
    }
}
