package ru.tinkoff.kora.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.logging.common.LoggingLevelApplier;
import ru.tinkoff.kora.logging.common.LoggingModule;

public interface LogbackModule extends LoggingModule {

    default LoggingLevelApplier loggingLevelApplier() {
        var ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        return new LoggingLevelApplier() {
            @Override
            public void apply(String logName, String logLevel) {
                ctx.getLogger(logName).setLevel(Level.toLevel(logLevel));
            }

            @Override
            public void reset() {
                for (var logger : ctx.getLoggerList()) {
                    if (logger.getName().equalsIgnoreCase("ROOT")) {
                        logger.setLevel(Level.INFO);
                    } else {
                        logger.setLevel(null);
                    }
                }
            }
        };
    }
}
