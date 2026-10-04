package io.koraframework.openfeature;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.openfeature.telemetry.OpenfeatureTelemetryConfig;
import org.jspecify.annotations.Nullable;

@ConfigMapper
public interface OpenfeatureConfig {

    default boolean initializeAsync() {
        return false;
    }

    OpenfeatureTelemetryConfig telemetry();

    /** Optional SDK domain for the provider and the default client. */
    @Nullable
    default String domain() {
        return null;
    }
}
