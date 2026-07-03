package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import tools.jackson.core.JsonGenerator;

import java.io.IOException;

public final class DefaultExceptionJsonWriter implements LoggingEventJsonWriter {

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
        gen.writeEndObject();
    }
}
