package ru.tinkoff.kora.scheduling.jdk;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface ScheduledExecutorServiceConfig {

    default int threads() {
        return 20;
    }
}
