package ru.tinkoff.kora.scheduling.common.telemetry;

public interface SchedulingTelemetryFactory {
    SchedulingTelemetry get(Class<?> jobClass, String jobMethod);
}
