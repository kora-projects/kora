package ru.tinkoff.kora.scheduling.quartz;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface SchedulingQuartzConfig {

    /**
     * @return Whether to wait for tasks to complete before scheduler shutdown during graceful shutdown.
     */
    default boolean waitForJobComplete() {
        return true;
    }
}
