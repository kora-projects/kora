package ru.tinkoff.kora.scheduling.quartz;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface SchedulingQuartzConfig {

    default boolean waitForJobComplete() {
        return true;
    }
}
