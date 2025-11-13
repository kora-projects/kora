package ru.tinkoff.kora.scheduling.common.telemetry;

public interface SchedulingTelemetry {

    Class<?> jobClass();

    String jobMethod();

    SchedulingObservation observe();
}
