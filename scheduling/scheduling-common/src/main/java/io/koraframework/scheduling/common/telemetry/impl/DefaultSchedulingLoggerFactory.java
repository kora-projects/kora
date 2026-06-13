package io.koraframework.scheduling.common.telemetry.impl;

import io.koraframework.logging.common.arg.StructuredArgumentWriter;
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
            this.logger.atDebug()
                .addKeyValue("scheduledJob", structuredJob(null, 0))
                .log("Scheduled Job execution started");
        }

        public void logEnd(@Nullable Throwable error, long durationInNanos) {
            if (error == null && !this.logger.isInfoEnabled()) {
                return;
            }
            if (error != null && !this.logger.isWarnEnabled()) {
                return;
            }

            this.logger.atLevel(error == null ? Level.INFO : Level.WARN)
                .addKeyValue("scheduledJob", structuredJob(error, durationInNanos))
                .setCause(error)
                .log(error == null ? "Scheduled Job execution completed" : "Scheduled Job execution failed with error");
        }

        protected StructuredArgumentWriter structuredJob(@Nullable Throwable error, long durationInNanos) {
            return gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("jobClass", this.context.jobClass().getCanonicalName());
                gen.writeStringProperty("jobMethod", this.context.jobMethod());
                if (durationInNanos > 0) {
                    gen.writeNumberProperty("duration", durationInNanos / 1_000_000);
                }
                if (error != null) {
                    var exceptionType = error.getClass().getCanonicalName();
                    if (exceptionType != null) {
                        gen.writeStringProperty("exceptionType", exceptionType);
                    }
                    gen.writeStringProperty("exceptionMessage", error.getMessage());
                }
                gen.writeEndObject();
            };
        }
    }
}
