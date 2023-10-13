package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public final class Slf4jSchedulingLoggerFactory implements SchedulingLoggerFactory {
    @Override
    @Nullable
    public SchedulingLogger get(TelemetryConfig.LogConfig logging, Class<?> jobClass, String jobMethod) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            var log = LoggerFactory.getLogger(jobClass.getCanonicalName() + "." + jobMethod);
            return new Slf4jSchedulingLogger(log, jobClass.getCanonicalName(), jobMethod);
        } else {
            return null;
        }
    }
}
