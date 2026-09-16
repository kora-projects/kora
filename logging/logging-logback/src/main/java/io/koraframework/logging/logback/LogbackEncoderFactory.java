package io.koraframework.logging.logback;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;

/**
 * Provides an {@link Encoder} for {@link KoraLogbackConfigurator} to log records with.
 * <p>
 * Factories are discovered via {@link java.util.ServiceLoader}, so putting a module on the classpath is enough to make
 * its encoder available. Exactly one of the discovered factories is used: the one named by
 * {@value KoraLogbackConfigurator#ENCODER_PROPERTY}, or, when nothing is named, the one with the highest
 * {@link #priority()}.
 *
 * @see KoraLogbackConfigurator
 */
public interface LogbackEncoderFactory {

    /**
     * @return name to select this factory by, case insensitive, for example {@code json}
     */
    String name();

    /**
     * @return relative priority used when no factory is selected explicitly, highest wins
     */
    default int priority() {
        return 0;
    }

    /**
     * @return encoder to log records with, not started yet
     */
    Encoder<ILoggingEvent> create(LoggerContext context);
}
