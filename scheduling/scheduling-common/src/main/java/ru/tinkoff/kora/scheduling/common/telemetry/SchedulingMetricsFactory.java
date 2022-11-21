package ru.tinkoff.kora.scheduling.common.telemetry;

public interface SchedulingMetricsFactory {
    SchedulingMetrics get(Class<?> jobClass, String jobMethod);
}
