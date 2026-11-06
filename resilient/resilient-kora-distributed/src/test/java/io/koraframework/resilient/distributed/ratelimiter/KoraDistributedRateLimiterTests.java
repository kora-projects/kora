package io.koraframework.resilient.distributed.ratelimiter;

import io.koraframework.resilient.ratelimiter.RateLimiterConfig;
import io.koraframework.resilient.ratelimiter.telemetry.impl.NoopRateLimiterTelemetry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class KoraDistributedRateLimiterTests {

    @ParameterizedTest
    @EnumSource(DistributedRateLimiterConfig.Algorithm.class)
    void zeroLimitForPeriodIsRejected(DistributedRateLimiterConfig.Algorithm algorithm) {
        var config = config(algorithm, 0, Duration.ofSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> new KoraDistributedRateLimiter("rl", config, new NoopClient(), NoopRateLimiterTelemetry.INSTANCE));
    }

    @ParameterizedTest
    @EnumSource(DistributedRateLimiterConfig.Algorithm.class)
    void subMillisecondLimitRefreshPeriodIsRejected(DistributedRateLimiterConfig.Algorithm algorithm) {
        var config = config(algorithm, 10, Duration.ofNanos(500));
        assertThrows(IllegalArgumentException.class, () -> new KoraDistributedRateLimiter("rl", config, new NoopClient(), NoopRateLimiterTelemetry.INSTANCE));
    }

    private static DistributedRateLimiterConfig config(DistributedRateLimiterConfig.Algorithm algorithm, int limitForPeriod, Duration limitRefreshPeriod) {
        return new DistributedRateLimiterConfig() {
            @Override
            public int limitForPeriod() {
                return limitForPeriod;
            }

            @Override
            public Duration limitRefreshPeriod() {
                return limitRefreshPeriod;
            }

            @Override
            public Algorithm algorithm() {
                return algorithm;
            }

            @Override
            public String keyPrefix() {
                return "test";
            }

            @Override
            public RateLimiterConfig.@Nullable TelemetryConfig telemetry() {
                return null;
            }
        };
    }

    private static final class NoopClient implements DistributedRateLimiterClient {

        @Override
        public long incrementAndExpire(String key, long ttlMillis) {
            return 1;
        }

        @Override
        public long addAndExpire(String key, long delta, long ttlMillis) {
            return delta;
        }

        @Override
        public void set(String key, long value, long ttlMillis) {
        }
    }
}
