package io.koraframework.resilient.ratelimiter;

import io.koraframework.resilient.ratelimiter.telemetry.impl.NoopRateLimiterTelemetry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class KoraRateLimiterTests {

    @ParameterizedTest
    @EnumSource(RateLimiterConfig.RateLimiterType.class)
    void zeroLimitForPeriodIsRejected(RateLimiterConfig.RateLimiterType type) {
        var config = config(type, 0, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> new KoraRateLimiter("rl", config, NoopRateLimiterTelemetry.INSTANCE));
    }

    @ParameterizedTest
    @EnumSource(RateLimiterConfig.RateLimiterType.class)
    void zeroLimitRefreshPeriodIsRejected(RateLimiterConfig.RateLimiterType type) {
        var config = config(type, 10, Duration.ZERO);
        assertThrows(IllegalArgumentException.class, () -> new KoraRateLimiter("rl", config, NoopRateLimiterTelemetry.INSTANCE));
    }

    private static RateLimiterConfig config(RateLimiterConfig.RateLimiterType type, int limitForPeriod, Duration limitRefreshPeriod) {
        return new RateLimiterConfig() {
            @Override
            public RateLimiterType type() {
                return type;
            }

            @Override
            public int limitForPeriod() {
                return limitForPeriod;
            }

            @Override
            public Duration limitRefreshPeriod() {
                return limitRefreshPeriod;
            }

            @Override
            public @Nullable TelemetryConfig telemetry() {
                return null;
            }
        };
    }
}
