package io.koraframework.resilient.ratelimiter.telemetry.impl;

import io.koraframework.resilient.ratelimiter.RateLimiter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultRateLimiterLoggerFactory {

    public static final DefaultRateLimiterLoggerFactory INSTANCE = new DefaultRateLimiterLoggerFactory();

    public DefaultRateLimiterLogger create(DefaultRateLimiterTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(RateLimiter.class.getCanonicalName() + "." + context.name());
        return new DefaultRateLimiterLogger(logger, context);
    }

    public static class DefaultRateLimiterLogger {

        protected final Logger logger;
        protected final DefaultRateLimiterTelemetry.TelemetryContext context;

        public DefaultRateLimiterLogger(Logger logger, DefaultRateLimiterTelemetry.TelemetryContext context) {
            this.logger = logger;
            this.context = context;
        }

        public void logStartAcquire() {
            if (!logger.isTraceEnabled()) {
                return;
            }
            logger.atTrace()
                .addKeyValue("resilientType", "ratelimiter")
                .addKeyValue("resilientName", this.context.name())
                .log("RateLimiter acquire started...");
        }

        public void logAcquire(boolean acquired, long processingTimeNanos, @Nullable Throwable exception) {
            if (exception != null) {
                if (!logger.isWarnEnabled()) {
                    return;
                }
                logger.atWarn()
                    .addKeyValue("resilientType", "ratelimiter")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("acquired", acquired)
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .addKeyValue("exceptionType", exception.getClass().getCanonicalName())
                    .addKeyValue("exceptionMessage", exception.getMessage())
                    .log("RateLimiter acquire failed");
            } else if (!acquired) {
                if (!logger.isWarnEnabled()) {
                    return;
                }
                logger.atWarn()
                    .addKeyValue("resilientType", "ratelimiter")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("acquired", false)
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .log("RateLimiter acquire rejected");
            } else if (logger.isDebugEnabled()) {
                logger.atDebug()
                    .addKeyValue("resilientType", "ratelimiter")
                    .addKeyValue("resilientName", this.context.name())
                    .addKeyValue("acquired", true)
                    .addKeyValue("processingTime", processingTimeNanos / 1_000_000)
                    .log("RateLimiter acquire recorded");
            }
        }
    }

}
