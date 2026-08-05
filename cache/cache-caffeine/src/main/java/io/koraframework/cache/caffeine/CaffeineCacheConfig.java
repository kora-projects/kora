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

    /**
     * @return Time after which a value is removed from the cache, counted from the moment the value was written.
     */
    @Nullable
    Duration expireAfterWrite();

    /**
     * @return Time after which a value is removed from the cache, counted from the moment the value was read.
     */
    @Nullable
    Duration expireAfterAccess();

    /**
     * @return Maximum cache size, upon reaching it or slightly earlier the least relevant values are evicted.
     */
    default Long maximumSize() {
        return 100_000L;
    }

    @Nullable
    Integer initialSize();

    CaffeineCacheTelemetryConfig telemetry();
}
