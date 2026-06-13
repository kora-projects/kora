package io.koraframework.scheduling.jdk;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface SchedulingJdkConfig {

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }
}
