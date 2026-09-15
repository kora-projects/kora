package io.koraframework.resilient.distributed.ratelimiter;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.resilient.ratelimiter.RateLimiterConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

@ConfigMapper
public interface DistributedRateLimiterConfig {

    enum Algorithm {
        /** Count-based fixed window: one counter per window, cheapest, a quota that allows up to 2x limit at boundaries. */
        FIXED_WINDOW,
        /** Token bucket (GCRA): continuous refill with a burst, exact in steady state and the general default. */
        TOKEN_BUCKET
    }

    default boolean enabled() {
        return true;
    }

    /**
     * Maximum number of permits granted per {@link #limitRefreshPeriod()}.
     */
    int limitForPeriod();

    /**
     * Length of the rate limiting window.
     */
    Duration limitRefreshPeriod();

    default Algorithm algorithm() {
        return Algorithm.TOKEN_BUCKET;
    }

    /**
     * Prefix applied to every counter key stored in the backend, namespacing this application's limiters.
     */
    String keyPrefix();

    /**
     * Per-operation telemetry overrides, reusing the local rate limiter telemetry config shape.
     */
    RateLimiterConfig.@Nullable TelemetryConfig telemetry();
}
