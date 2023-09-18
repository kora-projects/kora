package ru.tinkoff.kora.scheduling.common.telemetry;

import ru.tinkoff.kora.common.Context;

import jakarta.annotation.Nullable;

public interface SchedulingTracer {
    interface SchedulingSpan {
        void close(@Nullable Throwable exception);
    }

    SchedulingSpan createSpan(Context ctx);
}
