package io.koraframework.logging.logback.text;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import io.koraframework.logging.logback.KoraLogbackProperties;
import io.koraframework.logging.logback.LogbackEncoderFactory;

/**
 * The same layout as {@link ConsoleTextEncoderFactory}, with the timestamp and the level highlighted with ANSI escape
 * codes, meant for tests and local runs.
 * <p>
 * Selected automatically when running in a Gradle test worker, see {@link KoraLogbackProperties#isRunningInTests()},
 * and never outside of one. Enable it anywhere with {@code kora.logging.encoder=pretty} or
 * {@code KORA_LOGGING_ENCODER=pretty}, and opt out of it in tests by naming another encoder the same way, for example
 * {@code kora.logging.encoder=json} for a test asserting on JSON output.
 */
public final class ColorConsoleTextEncoderFactory implements LogbackEncoderFactory {

    public static final String NAME = "pretty";

    /** Above every other encoder of Kora, so colored logs win in tests. */
    public static final int TEST_PRIORITY = 1000;

    @Override
    public String name() {
        return NAME;
    }

    /**
     * @return the highest priority when running in tests, so colored logs are the default there, and the lowest one
     *         otherwise, so colors are never picked up by accident in production
     */
    @Override
    public int priority() {
        return KoraLogbackProperties.isRunningInTests()
            ? TEST_PRIORITY
            : Integer.MIN_VALUE;
    }

    @Override
    public Encoder<ILoggingEvent> create(LoggerContext context) {
        return new ConsoleTextRecordEncoder(true);
    }
}
