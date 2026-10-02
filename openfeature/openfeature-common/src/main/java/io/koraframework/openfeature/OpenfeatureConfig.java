package io.koraframework.openfeature;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

@ConfigValueExtractor
public interface OpenfeatureConfig {

    default boolean initializeAsync() {
        return false;
    }

    TelemetryConfig telemetry();
}
