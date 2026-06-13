package io.koraframework.scheduling.db;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface SchedulingDbConfig {

    default boolean initializeTable() {
        return false;
    }

    default Duration pollingInterval() {
        return Duration.ofSeconds(10);
    }

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    default String tableName() {
        return "scheduled_tasks";
    }
}
