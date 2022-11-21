package ru.tinkoff.kora.scheduling.common.telemetry;

import ru.tinkoff.kora.common.Context;

import javax.annotation.Nullable;

public interface SchedulingTracer {
    interface SchedulingSpan {
        void close(long duration, @Nullable Throwable exception);
    }

    SchedulingSpan createSpan(Context ctx);
}
