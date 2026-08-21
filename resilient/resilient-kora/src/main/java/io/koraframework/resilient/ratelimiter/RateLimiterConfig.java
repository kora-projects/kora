package io.koraframework.resilient.ratelimiter;

import io.koraframework.config.common.annotation.ConfigMapper;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Map;

@ConfigMapper
public interface RateLimiterConfig {

    enum RateLimiterType {
        /** Count-based fixed window: cheapest, a quota per window that allows up to 2x limit at window boundaries. */
        FIXED_WINDOW,
        /** Token bucket (GCRA): continuous refill with a burst, smoothest and the general default. */
        TOKEN_BUCKET
    }

    default boolean enabled() {
        return true;
    }

    default RateLimiterType type() {
        return RateLimiterType.TOKEN_BUCKET;
    }

    int limitForPeriod();

    Duration limitRefreshPeriod();

    @Nullable
    TelemetryConfig telemetry();

    @ConfigMapper
    interface TelemetryConfig {

        LoggingConfig logging();

        MetricsConfig metrics();

        TracingConfig tracing();

        @ConfigMapper
        interface LoggingConfig {

            @Nullable
            Boolean enabled();
        }

        @ConfigMapper
        interface MetricsConfig {

            @Nullable
            Boolean enabled();

            Duration @Nullable [] slo();

            @Nullable
            Map<String, String> tags();
        }

        @ConfigMapper
        interface TracingConfig {

            @Nullable
            Boolean enabled();

            @Nullable
            Map<String, String> attributes();
        }
    }
}
