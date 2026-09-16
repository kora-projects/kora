package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import io.koraframework.logging.logback.LogbackEncoderFactory;

/**
 * JSON logging with {@link JsonRecordEncoder}, preferred over plain text when this module is on the classpath.
 */
public final class JsonEncoderFactory implements LogbackEncoderFactory {

    public static final String NAME = "json";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public Encoder<ILoggingEvent> create(LoggerContext context) {
        return new JsonRecordEncoder();
    }
}
