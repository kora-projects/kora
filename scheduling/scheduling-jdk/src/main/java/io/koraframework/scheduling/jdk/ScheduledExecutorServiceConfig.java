package io.koraframework.scheduling.jdk;

import io.koraframework.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface ScheduledExecutorServiceConfig {

    default int threads() {
        return 2;
    }

    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }
}
