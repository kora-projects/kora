package io.koraframework.resilient.distributed.ratelimiter;

import io.koraframework.resilient.ratelimiter.RateLimiterConfig;
import io.koraframework.resilient.ratelimiter.telemetry.impl.NoopRateLimiterTelemetry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

class TokenBucketRateLimiterTests extends Assertions {

    /**
     * In-memory model of the Redis primitives (INCRBY + PEXPIRE, SET PX) with real TTL semantics.
     */
    static final class InMemoryClient implements DistributedRateLimiterClient {

        record Entry(long value, long expiresAt) {}

        private final Map<String, Entry> map = new HashMap<>();

        private long get(String key) {
            var e = map.get(key);
            if (e == null || e.expiresAt() <= System.currentTimeMillis()) {
                map.remove(key);
                return 0;
            }
            return e.value();
        }

        @Override
        public synchronized long incrementAndExpire(String key, long ttlMillis) {
            var existing = map.get(key);
            var v = get(key) + 1;
            long exp = (v == 1 || existing == null) ? System.currentTimeMillis() + ttlMillis : existing.expiresAt();
            map.put(key, new Entry(v, exp));
            return v;
        }

        @Override
        public synchronized long addAndExpire(String key, long delta, long ttlMillis) {
            var v = get(key) + delta;
            map.put(key, new Entry(v, System.currentTimeMillis() + ttlMillis));
            return v;
        }

        @Override
        public synchronized void set(String key, long value, long ttlMillis) {
            map.put(key, new Entry(value, System.currentTimeMillis() + ttlMillis));
        }
    }

    @Test
    void burstAfterLightTrafficIsBoundedByLimitForPeriod() throws InterruptedException {
        var config = new DistributedRateLimiterConfig() {
            @Override
            public int limitForPeriod() {
                return 10;
            }

            @Override
            public Duration limitRefreshPeriod() {
                return Duration.ofMillis(200);
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
        var limiter = new KoraDistributedRateLimiter("rl", config, new InMemoryClient(), NoopRateLimiterTelemetry.INSTANCE);

        // light steady traffic: 1 call / 60ms, below the limit of 10 / 200ms (1 call / 20ms), so the key never expires
        for (int i = 0; i < 30; i++) {
            assertTrue(limiter.tryAcquire());
            Thread.sleep(60);
        }

        // sudden burst: at most limitForPeriod permits (+1 for timing slack)
        int granted = 0;
        for (int i = 0; i < 1000; i++) {
            if (limiter.tryAcquire()) {
                granted++;
            }
        }
        assertTrue(granted <= 11, "burst after light traffic granted " + granted + " permits, limit is 10");
    }
}
