package ru.tinkoff.kora.cache.redis;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;
import java.util.Map;

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

    @ConfigValueExtractor
    interface RedisCacheTelemetryConfig {
        RedisCacheLoggingConfig logging();

        RedisCacheTracingConfig tracing();

        RedisCacheMetricsConfig metrics();

        @ConfigValueExtractor
        interface RedisCacheTracingConfig {
            default boolean enabled() {
                return false;
            }

            default Map<String, String> attributes() {
                return Map.of();
            }
        }

        @ConfigValueExtractor
        interface RedisCacheLoggingConfig extends TelemetryConfig.LogConfig {
        }

        @ConfigValueExtractor
        interface RedisCacheMetricsConfig {
            default boolean enabled() {
                return true;
            }
        }
    }
}
