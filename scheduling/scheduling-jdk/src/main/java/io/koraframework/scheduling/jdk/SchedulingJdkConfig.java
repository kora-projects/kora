package io.koraframework.scheduling.jdk;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.time.Duration;

@ConfigMapper
public interface SchedulingJdkConfig {

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }
}
