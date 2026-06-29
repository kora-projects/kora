package io.koraframework.resilient.retry.telemetry.impl;

import io.koraframework.resilient.retry.Retry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultRetryLoggerFactory {

    public static final DefaultRetryLoggerFactory INSTANCE = new DefaultRetryLoggerFactory();

    public DefaultRetryLogger create(DefaultRetryTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(Retry.class.getCanonicalName() + "." + context.name());
        return new DefaultRetryLogger(logger, context);
    }

    public static class DefaultRetryLogger {

        protected final Logger logger;
        protected final DefaultRetryTelemetry.TelemetryContext context;

        public DefaultRetryLogger(Logger logger, DefaultRetryTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logStartRetry() {
            if (!logger.isTraceEnabled()) {
                return;
            }
            logger.atTrace()
                .addKeyValue("resilientType", "retry")
                .addKeyValue("resilientName", this.context.name())
                .log("Retry started...");
        }

        public void logRetry(int attempts, boolean exhausted, long lastDelayInNanos, long processingTimeNanos, @Nullable Throwable exception) {
            if (exhausted) {
                if (!logger.isWarnEnabled()) {
                    return;
                }
                var event = logger.atWarn()
                    .addKeyValue("resilientType", "retry")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("attempts", attempts)
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000);
                addException(event, exception);
                event.log("Retry exhausted");
            } else if (attempts > 0 && logger.isDebugEnabled()) {
                var event = logger.atDebug()
                    .addKeyValue("resilientType", "retry")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("attempts", attempts)
                    .addKeyValue("lastDelay", lastDelayInNanos)
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000);
                addException(event, exception);
                event.log("Retry attempts recorded");
            }
        }

        private static void addException(org.slf4j.spi.LoggingEventBuilder event, @Nullable Throwable exception) {
            if (exception != null) {
                event.addKeyValue("exceptionType", exception.getClass().getCanonicalName())
                    .addKeyValue("exceptionMessage", exception.getMessage());
            }
        }
    }

}
