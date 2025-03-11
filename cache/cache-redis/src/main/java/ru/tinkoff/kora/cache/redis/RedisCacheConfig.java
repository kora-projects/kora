package ru.tinkoff.kora.cache.redis;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

/**
 * This module is no longer maintained, it was replaced with new one.
 * <p>
 * Use dependency - ru.tinkoff.kora:cache-redis-lettuce AND RedisCacheConfig
 * <p>
 * Check documentation for more information
 */
@Deprecated
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
}
