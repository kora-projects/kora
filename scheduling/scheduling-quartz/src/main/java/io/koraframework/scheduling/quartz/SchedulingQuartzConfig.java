package io.koraframework.scheduling.quartz;

import io.koraframework.config.common.annotation.ConfigMapper;

@ConfigMapper
public interface SchedulingQuartzConfig {

    /**
     * @return Whether to wait for tasks to complete before scheduler shutdown during graceful shutdown.
     */
    default boolean waitForJobComplete() {
        return true;
    }
}
