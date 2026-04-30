package io.koraframework.cache.caffeine;


import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;
import org.jspecify.annotations.Nullable;

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
        interface CaffeineLoggingConfig extends TelemetryConfig.LogConfig { }

        @ConfigValueExtractor
        interface CaffeineMetricsConfig {

            default boolean enabled() {
                return false;
            }

            default Map<String, String> tags() {
                return Map.of();
            }
        }
    }
}
