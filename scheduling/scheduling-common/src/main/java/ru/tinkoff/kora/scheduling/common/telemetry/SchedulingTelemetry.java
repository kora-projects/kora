package ru.tinkoff.kora.scheduling.common.telemetry;

import ru.tinkoff.kora.common.Context;

import jakarta.annotation.Nullable;

public interface SchedulingTelemetry {
    interface SchedulingTelemetryContext {
        void close(@Nullable Throwable exception);
    }

    Class<?> jobClass();

    String jobMethod();

    SchedulingTelemetryContext get(Context ctx);
}
