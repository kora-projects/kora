package io.koraframework.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackModuleTest {

    @Test
    void unknownLevelIsIgnored() {
        var applier = new LogbackModule() {}.logbackLoggingLevelApplier();
        var logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("logback.module.test.unknown");
        try {
            applier.apply(logger.getName(), "WARN");
            applier.apply(logger.getName(), "WARNNG");
            assertThat(logger.getLevel()).isEqualTo(Level.WARN);
        } finally {
            logger.setLevel(null);
        }
    }

    @Test
    void knownLevelIsApplied() {
        var applier = new LogbackModule() {}.logbackLoggingLevelApplier();
        var logger = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("logback.module.test.known");
        try {
            applier.apply(logger.getName(), "error");
            assertThat(logger.getLevel()).isEqualTo(Level.ERROR);
        } finally {
            logger.setLevel(null);
        }
    }
}
