package io.koraframework.scheduling.quartz;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface SchedulingQuartzConfig {

    default boolean waitForJobComplete() {
        return true;
    }
}
