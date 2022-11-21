package ru.tinkoff.kora.scheduling.common.telemetry;

import javax.annotation.Nullable;

public interface SchedulingLogger {
    void logJobStart();

    void logJobFinish(long duration, @Nullable Throwable e);
}
