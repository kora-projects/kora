package io.koraframework.logging.logback.json.writer;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import io.koraframework.logging.common.arg.StructuredArgument;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

public final class DefaultExceptionJsonWriterLogging implements LoggingEventJsonWriter {

    @Override
    public void write(JsonGenerator gen, ILoggingEvent event) throws IOException {
        var throwable = event.getThrowableProxy();
        if (throwable == null) {
            return;
        }
        gen.writeName("exception");
        gen.writeStartObject();
        gen.writeStringProperty("class", throwable.getClassName());
        gen.writeStringProperty("message", throwable.getMessage());
        gen.writeStringProperty("stackTrace", ThrowableProxyUtil.asString(throwable));
        var data = this.findExceptionData(event);
        if (data != null) {
            gen.writeName("data");
            data.writeTo(gen);
        }
        gen.writeEndObject();
    }

    private StructuredArgumentWriter findExceptionData(ILoggingEvent event) {
        var markers = event.getMarkerList();
        if (markers != null) {
            for (var marker : markers) {
                if (marker instanceof StructuredArgument structuredArgument && this.isExceptionField(structuredArgument.fieldName())) {
                    return structuredArgument;
                }
            }
        }

        var arguments = event.getArgumentArray();
        if (arguments != null) {
            for (var argument : arguments) {
                if (argument instanceof StructuredArgument structuredArgument && this.isExceptionField(structuredArgument.fieldName())) {
                    return structuredArgument;
                }
            }
        }

        var keyValuePairs = event.getKeyValuePairs();
        if (keyValuePairs != null) {
            for (var keyValuePair : keyValuePairs) {
                if (this.isExceptionField(keyValuePair.key) && keyValuePair.value instanceof StructuredArgumentWriter structuredArgumentWriter) {
                    return structuredArgumentWriter;
                }
            }
        }

        return null;
    }

    private boolean isExceptionField(String fieldName) {
        return "exception".equals(fieldName) || "throwable".equals(fieldName);
    }
}
