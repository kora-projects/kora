package io.koraframework.logging.logback.text;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import io.koraframework.logging.logback.LogbackEncoderFactory;

/**
 * Plain text logging with {@link ConsoleTextRecordEncoder}, the default when nothing else is on the classpath.
 */
public final class ConsoleTextEncoderFactory implements LogbackEncoderFactory {

    public static final String NAME = "text";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Encoder<ILoggingEvent> create(LoggerContext context) {
        return new ConsoleTextRecordEncoder();
    }
}
