package io.koraframework.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.koraframework.common.annotation.DefaultComponent;
import org.slf4j.LoggerFactory;
import io.koraframework.logging.common.LoggingLevelApplier;
import io.koraframework.logging.common.LoggingModule;

public interface LogbackModule extends LoggingModule {

    @DefaultComponent
    default LoggingLevelApplier logbackLoggingLevelApplier() {
        var ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        return new LogbackLoggingLevelApplierImpl(ctx);
    }

    final class LogbackLoggingLevelApplierImpl implements LoggingLevelApplier {
        private final LoggerContext ctx;

        LogbackLoggingLevelApplierImpl(LoggerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void apply(String logName, String logLevel) {
            this.ctx.getLogger(logName).setLevel(Level.toLevel(logLevel));
        }

        @Override
        public void reset() {
            var loggers = this.ctx.getLoggerList();
            for (int i = 0; i < loggers.size(); i++) {
                var logger = loggers.get(i);
                if (logger.getName().equalsIgnoreCase("ROOT")) {
                    logger.setLevel(Level.INFO);
                } else {
                    logger.setLevel(null);
                }
            }
        }
    }
}
