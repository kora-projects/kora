package io.koraframework.resilient.timeout.telemetry.impl;

import io.koraframework.resilient.timeout.Timeout;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultTimeoutLoggerFactory {

    public static final DefaultTimeoutLoggerFactory INSTANCE = new DefaultTimeoutLoggerFactory();

    public DefaultTimeoutLogger create(DefaultTimeoutTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(Timeout.class.getCanonicalName() + "." + context.name());
        return new DefaultTimeoutLogger(logger, context);
    }

    public static class DefaultTimeoutLogger {

        protected final Logger logger;
        protected final DefaultTimeoutTelemetry.TelemetryContext context;

        public DefaultTimeoutLogger(Logger logger, DefaultTimeoutTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logStartWaiting(long timeToWaitInNanos) {
            if (logger.isTraceEnabled()) {
                var event = logger.atTrace()
                    .addKeyValue("resilientType", "timeout")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("timeToWait", timeToWaitInNanos);
                event.log("Timeout await started...");
            }
        }

        public void logTimeout(long timeoutInNanos, long processingTimeNanos, @Nullable Throwable exception) {
            if (!logger.isWarnEnabled()) {
                return;
            }
            var event = logger.atWarn()
                .addKeyValue("resilientType", "timeout")
                .addKeyValue("resilientName", this.context.name())
                .addKeyValue("timeout", timeoutInNanos)
                .addKeyValue("processingTime", processingTimeNanos / 1_000_000);
            if (exception != null) {
                event.addKeyValue("exceptionType", exception.getClass().getCanonicalName())
                    .addKeyValue("exceptionMessage", exception.getMessage());
            }
            event.log("Timeout exhausted");
        }
    }

}
