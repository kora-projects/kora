package ru.tinkoff.kora.scheduling.common.telemetry;

import org.slf4j.Logger;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import javax.annotation.Nullable;

public final class Slf4jSchedulingLogger implements SchedulingLogger {
    private final Logger logger;
    private final String jobClass;
    private final String jobMethod;

    public Slf4jSchedulingLogger(Logger logger, String jobClass, String jobMethod) {
        this.logger = logger;
        this.jobClass = jobClass;
        this.jobMethod = jobMethod;
    }

    @Override
    public void logJobStart() {
        if (!this.logger.isInfoEnabled()) {
            return;
        }
        var arg = StructuredArgument.marker("scheduledJob", gen -> {
            gen.writeStartObject();
            gen.writeStringField("jobClass", this.jobClass);
            gen.writeStringField("jobMethod", this.jobMethod);
            gen.writeEndObject();
        });
        this.logger.debug(arg, "Starting SLF4J scheduling job");
    }

    @Override
    public void logJobFinish(long duration, @Nullable Throwable e) {
        if (!this.logger.isWarnEnabled()) {
            return;
        }
        if (e == null && !this.logger.isInfoEnabled()) {
            return;
        }
        var arg = StructuredArgument.marker("scheduledJob", gen -> {
            gen.writeStartObject();
            gen.writeStringField("jobClass", this.jobClass);
            gen.writeStringField("jobMethod", this.jobMethod);
            long durationMs = duration / 1_000_000;
            gen.writeNumberField("duration", durationMs);
            gen.writeEndObject();
        });

        if (e != null) {
            this.logger.warn(arg, "Finished SLF4J scheduling job with error", e);
        } else {
            this.logger.info(arg, "Finished SLF4J scheduling job");
        }
    }
}
