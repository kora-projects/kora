package ru.tinkoff.kora.scheduling.common.telemetry;

import org.slf4j.Logger;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import javax.annotation.Nullable;

public final class Slf4jSchedulingLogger implements SchedulingLogger {
    private final Logger log;
    private final String jobClass;
    private final String jobMethod;

    public Slf4jSchedulingLogger(Logger log, String jobClass, String jobMethod) {
        this.log = log;
        this.jobClass = jobClass;
        this.jobMethod = jobMethod;
    }

    @Override
    public void logJobStart() {
        if (!this.log.isInfoEnabled()) {
            return;
        }
        var arg = StructuredArgument.marker("scheduledJob", gen -> {
            gen.writeStartObject();
            gen.writeStringField("jobClass", this.jobClass);
            gen.writeStringField("jobMethod", this.jobMethod);
            gen.writeEndObject();
        });
        this.log.info(arg, "Starting job");
    }

    @Override
    public void logJobFinish(long duration, @Nullable Throwable e) {
        if (!this.log.isWarnEnabled()) {
            return;
        }
        if (e == null && !this.log.isInfoEnabled()) {
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
            this.log.warn(arg, "Job finished with error", e);
        } else {
            this.log.info(arg, "Job finished");
        }
    }
}
