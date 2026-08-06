package io.koraframework.logging.logback.json.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import io.koraframework.logging.logback.KoraLoggingEvent;
import io.koraframework.logging.logback.json.JsonFieldConstants;
import org.slf4j.event.KeyValuePair;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.io.SerializedString;
import tools.jackson.core.util.JsonGeneratorDelegate;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultStructuredJsonWriterLogging implements LoggingEventJsonWriter {

    private static final Map<String, SerializedString> MDC_KEY_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, SerializedString> ARG_KEY_CACHE = new ConcurrentHashMap<>();

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

        gen.writeName(JsonFieldConstants.MDC);
        gen.writeStartObject();
        for (var entry : koraMdc.entrySet()) {
            var key = getMdcKey(entry.getKey());
            gen.writeName(key);
            entry.getValue().writeTo(gen);
        }
        for (var entry : slf4jMdc.entrySet()) {
            if (!koraMdc.containsKey(entry.getKey())) {
                var key = getMdcKey(entry.getKey());
                gen.writeName(key);
                gen.writeString(entry.getValue());
            }
        }
        gen.writeEndObject();
    }

    private static SerializedString getMdcKey(String key) {
        return MDC_KEY_CACHE.computeIfAbsent(key, SerializedString::new);
    }

    private Map<String, StructuredArgumentWriter> koraMdc(ILoggingEvent event) {
        if (event instanceof KoraLoggingEvent koraEvent) {
            return koraEvent.koraMdc();
        }
        return Map.of();
    }

    private void writeStructuredArguments(JsonGenerator gen, ILoggingEvent event) throws IOException {
        var structuredArguments = getStructuredArguments(event);

        var data = this.findData(structuredArguments);
        this.writeData(gen, data);

        if (this.hasAttributes(structuredArguments, event.getKeyValuePairs())) {
            gen.writeName(JsonFieldConstants.ARGS);
            gen.writeStartObject();
            for (var argument : structuredArguments) {
                if (!this.isData(argument)) {
                    var key = getArgKey(argument.fieldName());
                    gen.writeName(key);
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

    private List<StructuredArgument> getStructuredArguments(ILoggingEvent event) {
        var result = new ArrayList<StructuredArgument>();

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

        return result;
    }

    private List<StructuredArgument> findData(List<StructuredArgument> structuredArguments) {
        var data = new ArrayList<StructuredArgument>();
        for (var argument : structuredArguments) {
            if (this.isData(argument)) {
                data.add(argument);
            }
        }
        return data;
    }

    private void writeData(JsonGenerator gen, List<StructuredArgument> data) throws IOException {
        if (data.isEmpty()) {
            return;
        }
        gen.writeName(JsonFieldConstants.DATA);
        if (data.size() == 1) {
            data.getFirst().writeTo(gen);
        } else {
            gen.writeStartObject();
            for (var argument : data) {
                argument.writeTo(new UnwrappedObjectJsonGenerator(gen));
            }
            gen.writeEndObject();
        }
    }

    private boolean isData(StructuredArgument argument) {
        return "data".equals(argument.fieldName());
    }

    private boolean hasAttributes(List<StructuredArgument> structuredArguments, List<KeyValuePair> keyValuePairs) {
        for (var argument : structuredArguments) {
            if (!this.isData(argument)) {
                return true;
            }
        }
        return keyValuePairs != null && !keyValuePairs.isEmpty();
    }

    private void writeKeyValuePair(JsonGenerator gen, KeyValuePair keyValuePair) throws IOException {
        var key = getArgKey(keyValuePair.key);
        gen.writeName(key);
        switch (keyValuePair.value) {
            case StructuredArgumentWriter structuredArgumentWriter -> structuredArgumentWriter.writeTo(gen);
            case null -> gen.writeNull();
            case String value -> gen.writeString(value);
            case Integer value -> gen.writeNumber(value);
            case Long value -> gen.writeNumber(value);
            case Boolean value -> gen.writeBoolean(value);
            case Double value -> gen.writeNumber(value);
            case Float value -> gen.writeNumber(value);
            case Short value -> gen.writeNumber(value);
            case Byte value -> gen.writeNumber(value);
            case BigInteger value -> gen.writeNumber(value);
            case BigDecimal value -> gen.writeNumber(value);
            default -> gen.writeString(keyValuePair.value.toString());
        }
    }

    private static SerializedString getArgKey(String key) {
        return ARG_KEY_CACHE.computeIfAbsent(key, SerializedString::new);
    }

    private static final class UnwrappedObjectJsonGenerator extends JsonGeneratorDelegate {
        private boolean unwrapped;
        private int depth;

        private UnwrappedObjectJsonGenerator(JsonGenerator delegate) {
            super(delegate);
        }

        @Override
        public JsonGenerator writeStartObject() throws JacksonException {
            if (!this.unwrapped) {
                this.unwrapped = true;
                return this;
            }
            this.depth++;
            return super.writeStartObject();
        }

        @Override
        public JsonGenerator writeStartObject(Object forValue) throws JacksonException {
            return this.writeStartObject();
        }

        @Override
        public JsonGenerator writeStartObject(Object forValue, int size) throws JacksonException {
            return this.writeStartObject();
        }

        @Override
        public JsonGenerator writeEndObject() throws JacksonException {
            if (this.unwrapped && this.depth == 0) {
                return this;
            }
            this.depth--;
            return super.writeEndObject();
        }
    }
}
