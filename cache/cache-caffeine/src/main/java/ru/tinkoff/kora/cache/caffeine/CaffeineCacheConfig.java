package ru.tinkoff.kora.cache.caffeine;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.Map;

@ConfigValueExtractor
public interface CaffeineCacheConfig {

    @Nullable
    Duration expireAfterWrite();

    @Nullable
    Duration expireAfterAccess();

    default Long maximumSize() {
        return 100_000L;
    }

    @Nullable
    Integer initialSize();

    CaffeineTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface CaffeineTelemetryConfig {

        CaffeineMetricsConfig metrics();

        CaffeineLoggingConfig logging();

        @ConfigValueExtractor
        interface CaffeineLoggingConfig {
            default boolean enabled() {
                return false;
            }
        }

        @ConfigValueExtractor
        interface CaffeineMetricsConfig {
            default boolean enabled() {
                return true;
            }

            default Map<String, String> tags() {
                return Map.of();
            }
        }
    }

}
