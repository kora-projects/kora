package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;

public interface SchedulingLogger {
    void logJobStart();

    void logJobFinish(long duration, @Nullable Throwable e);
}
