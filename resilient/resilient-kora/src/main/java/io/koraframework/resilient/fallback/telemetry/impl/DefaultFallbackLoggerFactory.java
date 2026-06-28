package io.koraframework.resilient.fallback.telemetry.impl;

import io.koraframework.resilient.fallback.Fallback;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultFallbackLoggerFactory {

    public static final DefaultFallbackLoggerFactory INSTANCE = new DefaultFallbackLoggerFactory();

    public DefaultFallbackLogger create(DefaultFallbackTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(Fallback.class.getCanonicalName() + "." + context.name());
        return new DefaultFallbackLogger(logger, context);
    }

    public static class DefaultFallbackLogger {

        protected final Logger logger;
        protected final DefaultFallbackTelemetry.TelemetryContext context;

        public DefaultFallbackLogger(Logger logger, DefaultFallbackTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logStartFallback() {
            if (!logger.isTraceEnabled()) {
                return;
            }
            logger.atTrace()
                .addKeyValue("resilientType", "fallback")
                .addKeyValue("resilientName", this.context.name())
                .log("Fallback started...");
        }

        public void logExecute(long processingTimeNanos, Throwable throwable, @Nullable Throwable exception) {
            if (!logger.isWarnEnabled()) {
                return;
            }
            var event = logger.atWarn()
                .addKeyValue("resilientType", "fallback")
                .addKeyValue("resilientName", this.context.name())
                .addKeyValue("processingTime", processingTimeNanos / 1_000_000);
            addException(event, throwable);
            if (exception != null && exception != throwable) {
                event.addKeyValue("telemetryExceptionType", exception.getClass().getCanonicalName())
                    .addKeyValue("telemetryExceptionMessage", exception.getMessage());
            }
            event.log("Fallback executed");
        }

        private static void addException(org.slf4j.spi.LoggingEventBuilder event, Throwable throwable) {
            var errorType = throwable.getClass().getCanonicalName();
            if (errorType != null) {
                event.addKeyValue("exceptionType", errorType);
            }
            if (throwable.getMessage() != null) {
                event.addKeyValue("exceptionMessage", throwable.getMessage());
            }
        }
    }

}
