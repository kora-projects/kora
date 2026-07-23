package ru.tinkoff.kora.scheduling.jdk;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface ScheduledExecutorServiceConfig {

    /**
     * @return Maximum number of threads in the ScheduledExecutorService.
     */
    default int threads() {
        return 2;
    }

    /**
     * @return Time to wait for tasks to complete before scheduler shutdown during graceful shutdown.
     */
    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }
}
