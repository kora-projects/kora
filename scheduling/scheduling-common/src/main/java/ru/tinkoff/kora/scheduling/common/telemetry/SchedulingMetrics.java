package ru.tinkoff.kora.scheduling.common.telemetry;

import jakarta.annotation.Nullable;

public interface SchedulingMetrics {
    void record(long processingTimeNanos, @Nullable Throwable e);
}
