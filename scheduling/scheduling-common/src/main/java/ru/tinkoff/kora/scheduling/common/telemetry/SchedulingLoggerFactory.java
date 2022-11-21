package ru.tinkoff.kora.scheduling.common.telemetry;

public interface SchedulingLoggerFactory {
    SchedulingLogger get(Class<?> jobClass, String jobMethod);
}
