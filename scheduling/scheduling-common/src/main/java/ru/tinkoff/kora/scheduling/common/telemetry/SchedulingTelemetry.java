package ru.tinkoff.kora.scheduling.common.telemetry;

import ru.tinkoff.kora.common.Context;

import javax.annotation.Nullable;

public interface SchedulingTelemetry {
    interface SchedulingTelemetryContext {
        void close(@Nullable Throwable exception);
    }

    SchedulingTelemetryContext get(Context ctx);
}
