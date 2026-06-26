package io.koraframework.scheduling.common.telemetry.impl;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

public class DefaultSchedulingLoggerFactory {

    public static final DefaultSchedulingLoggerFactory INSTANCE = new DefaultSchedulingLoggerFactory();

    public DefaultSchedulingLogger create(DefaultSchedulingTelemetry.TelemetryContext context) {
        return new DefaultSchedulingLogger(context, LoggerFactory.getLogger(context.jobName()));
    }

    public static class DefaultSchedulingLogger {

        protected final DefaultSchedulingTelemetry.TelemetryContext context;
        protected final Logger logger;

        public DefaultSchedulingLogger(DefaultSchedulingTelemetry.TelemetryContext context, Logger logger) {
            this.context = context;
            this.logger = logger;
        }

        public void logStart() {
            if (!this.logger.isDebugEnabled()) {
                return;
            }

            var log = this.logger.atDebug();
            if (this.context.jobConfigPath() != null) {
                log = log.addKeyValue("jobConfigPath", this.context.jobConfigPath());
            }

            log.addKeyValue("jobClass", this.context.jobCanonicalName())
                .addKeyValue("jobMethod", this.context.jobMethod())
                .log("Scheduled Job execution started");
        }

        public void logEnd(@Nullable Throwable error, long durationInNanos) {
            if (error == null && !this.logger.isInfoEnabled()) {
                return;
            }
            if (error != null && !this.logger.isWarnEnabled()) {
                return;
            }

            var log = this.logger.atLevel(error == null ? Level.INFO : Level.WARN);
            if (this.context.jobConfigPath() != null) {
                log = log.addKeyValue("jobConfigPath", this.context.jobConfigPath());
            }

            log = log.addKeyValue("jobClass", this.context.jobCanonicalName())
                .addKeyValue("jobMethod", this.context.jobMethod())
                .addKeyValue("duration", durationInNanos / 1_000_000)
                .setCause(error);
            if (error != null) {
                var exceptionType = error.getClass().getCanonicalName();
                if (exceptionType != null) {
                    log = log.addKeyValue("exceptionType", exceptionType);
                }
                if (error.getMessage() != null) {
                    log = log.addKeyValue("exceptionMessage", error.getMessage());
                }
            }
            log.log(error == null ? "Scheduled Job execution completed" : "Scheduled Job execution failed with error");
        }
    }
}
