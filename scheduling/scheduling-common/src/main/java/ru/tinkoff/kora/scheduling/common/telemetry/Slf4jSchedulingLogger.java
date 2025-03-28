package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

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

        this.logger.debug(arg, "Scheduled Job execution started...");
    }

    @Override
    public void logJobFinish(long durationInNanos, @Nullable Throwable e) {
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
            long durationMs = durationInNanos / 1_000_000;
            gen.writeNumberField("duration", durationMs);
            gen.writeEndObject();
        });

        if (e != null) {
            this.logger.error(arg, "Scheduled Job execution failed with error", e);
        } else if (logger.isInfoEnabled()) {
            this.logger.info(arg, "Scheduled Job execution complete in {}", TimeUtils.durationForLogging(durationInNanos));
        }
    }
}
