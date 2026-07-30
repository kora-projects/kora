package io.koraframework.cache.caffeine;


import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryConfig;
import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@ConfigMapper
public interface CaffeineCacheConfig {

    default boolean enabled() {
        return true;
    }

    @Nullable
    Duration expireAfterWrite();

    @Nullable
    Duration expireAfterAccess();

    default Long maximumSize() {
        return 100_000L;
    }

    @Nullable
    Integer initialSize();

    CaffeineCacheTelemetryConfig telemetry();
}
