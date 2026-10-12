package io.koraframework.resilient.ratelimiter;

import io.koraframework.resilient.ratelimiter.telemetry.impl.NoopRateLimiterTelemetry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class FixedWindowKoraRateLimiterTests extends Assertions {

    private static RateLimiterConfig config(int limitForPeriod, Duration period) {
        return new RateLimiterConfig() {
            @Override
            public RateLimiterType type() {
                return RateLimiterType.FIXED_WINDOW;
            }

            @Override
            public int limitForPeriod() {
                return limitForPeriod;
            }

            @Override
            public Duration limitRefreshPeriod() {
                return period;
            }

            @Override
            public @Nullable TelemetryConfig telemetry() {
                return null;
            }
        };
    }

    @Test
    void limitAbove24BitsIsEnforced() {
        int limit = 20_000_000; // > 2^24
        var rateLimiter = new KoraRateLimiter("rl", config(limit, Duration.ofHours(1)), NoopRateLimiterTelemetry.INSTANCE);
        long granted = 0;
        for (int i = 0; i < limit + 1_000_000; i++) {
            if (rateLimiter.tryAcquire()) {
                granted++;
            }
        }
        assertEquals(limit, granted);
    }
}
