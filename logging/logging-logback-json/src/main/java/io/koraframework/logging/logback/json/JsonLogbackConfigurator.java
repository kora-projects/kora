package io.koraframework.logging.logback.json;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ConfiguratorRank;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import io.koraframework.logging.logback.KoraLogbackConfigurator;

/**
 * Configures JSON logging with {@link JsonRecordEncoder}.
 * <p>
 * Ranked above {@link io.koraframework.logging.logback.ConsoleTextLogbackConfigurator}, so JSON logging wins when both
 * modules are on the classpath. Extend it and override {@link #createEncoder(LoggerContext)} to customize writers or
 * masking, then select the subclass with the {@code logback.configuratorClass} system property.
 */
@ConfiguratorRank(ConfiguratorRank.CUSTOM_NORMAL_PRIORITY)
public class JsonLogbackConfigurator extends KoraLogbackConfigurator {

    @Override
    protected Encoder<ILoggingEvent> createEncoder(LoggerContext context) {
        return new JsonRecordEncoder();
    }
}
