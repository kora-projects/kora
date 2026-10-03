package io.koraframework.resilient.bulkhead.telemetry.impl;

import io.koraframework.resilient.bulkhead.Bulkhead;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultBulkheadLoggerFactory {

    public static final DefaultBulkheadLoggerFactory INSTANCE = new DefaultBulkheadLoggerFactory();

    public DefaultBulkheadLogger create(DefaultBulkheadTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(Bulkhead.class.getCanonicalName() + "." + context.name());
        return new DefaultBulkheadLogger(logger, context);
    }

    public static class DefaultBulkheadLogger {

        protected final Logger logger;
        protected final DefaultBulkheadTelemetry.TelemetryContext context;

        public DefaultBulkheadLogger(Logger logger, DefaultBulkheadTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logStartAcquire() {
            if (!logger.isTraceEnabled()) {
                return;
            }
            logger.atTrace()
                .addKeyValue("resilientType", "bulkhead")
                .addKeyValue("resilientName", this.context.name())
                .log("Bulkhead admission started...");
        }

        public void logAcquire(boolean acquired, long processingTimeNanos, @Nullable Throwable exception) {
            if (exception != null) {
                if (!logger.isWarnEnabled()) {
                    return;
                }
                logger.atWarn()
                    .addKeyValue("resilientType", "bulkhead")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("acquired", acquired)
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .addKeyValue("exceptionType", exception.getClass().getCanonicalName())
                    .addKeyValue("exceptionMessage", exception.getMessage())
                    .log("Bulkhead call failed");
            } else if (!acquired) {
                if (!logger.isWarnEnabled()) {
                    return;
                }
                logger.atWarn()
                    .addKeyValue("resilientType", "bulkhead")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("acquired", false)
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .log("Bulkhead acquire rejected");
            } else if (logger.isDebugEnabled()) {
                logger.atDebug()
                    .addKeyValue("resilientType", "bulkhead")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("acquired", true)
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .log("Bulkhead call completed");
            }
        }
    }
}
