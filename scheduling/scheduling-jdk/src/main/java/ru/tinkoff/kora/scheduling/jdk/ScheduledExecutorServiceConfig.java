package ru.tinkoff.kora.scheduling.jdk;

import jakarta.annotation.Nullable;

public record ScheduledExecutorServiceConfig(int threads) {
    public ScheduledExecutorServiceConfig(@Nullable Integer threads) {
        this(
            threads != null ? threads : 10
        );
    }
}
