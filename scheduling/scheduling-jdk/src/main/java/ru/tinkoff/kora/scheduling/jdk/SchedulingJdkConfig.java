package ru.tinkoff.kora.scheduling.jdk;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface SchedulingJdkConfig {

    default int threads() {
        return 20;
    }

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }
}
