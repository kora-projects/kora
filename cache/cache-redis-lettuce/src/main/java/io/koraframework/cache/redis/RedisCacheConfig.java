package io.koraframework.cache.redis;


import io.koraframework.cache.redis.telemetry.RedisCacheTelemetryConfig;
import io.koraframework.config.common.annotation.ConfigValueExtractor;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@ConfigValueExtractor
public interface RedisCacheConfig {

    /**
     * Key prefix allow to avoid key collision in single Redis database between multiple caches
     *
     * @return Redis Cache key prefix, if empty string means that prefix will NOT be applied
     */
    String keyPrefix();

    @Nullable
    Duration expireAfterWrite();

    @Nullable
    Duration expireAfterAccess();

    RedisCacheTelemetryConfig telemetry();
}
