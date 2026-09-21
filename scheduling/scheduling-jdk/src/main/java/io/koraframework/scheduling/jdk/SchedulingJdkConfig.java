package io.koraframework.scheduling.jdk;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.time.Duration;

@ConfigMapper
public interface SchedulingJdkConfig {

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    /**
     * Maximum number of job executions running at the same time.
     *
     * <p>Unlimited by default, because a job never overlaps its own execution and the executor is
     * shared by all jobs of the application.
     */
    default int maxConcurrentExecutions() {
        return Integer.MAX_VALUE;
    }
}
