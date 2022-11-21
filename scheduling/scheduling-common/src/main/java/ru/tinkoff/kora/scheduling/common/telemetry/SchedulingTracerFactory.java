package ru.tinkoff.kora.scheduling.common.telemetry;

public interface SchedulingTracerFactory {
    SchedulingTracer get(Class<?> jobClass, String jobMethod);
}
