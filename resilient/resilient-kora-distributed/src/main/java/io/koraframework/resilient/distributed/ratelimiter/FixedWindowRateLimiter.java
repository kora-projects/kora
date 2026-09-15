package io.koraframework.resilient.distributed.ratelimiter;

import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetry;
import org.jspecify.annotations.Nullable;

/**
 * Count-based fixed window distributed rate limiter (algorithm A).
 *
 * <p>Each window owns a distinct counter key ({@code <keyBase>:<windowId>}) whose value is bumped with an atomic
 * increment; a permit is granted while the counter stays within {@link DistributedRateLimiterConfig#limitForPeriod()}.
 * The counter is created afresh every window and expires on its own, so idle keys cost nothing.
 *
 * <p>Cheapest option (a single atomic increment per call) and a quota-per-window semantic, at the cost of allowing up to
 * twice the limit across the boundary of two adjacent windows. Use {@link TokenBucketRateLimiter} for a smooth continuous
 * rate.
 */
final class FixedWindowRateLimiter extends AbstractDistributedRateLimiter {

    FixedWindowRateLimiter(String name,
                                  String keyBase,
                                  DistributedRateLimiterConfig config,
                                  DistributedRateLimiterClient client,
                                  @Nullable RateLimiterTelemetry telemetry) {
        super(name, keyBase, config, client, telemetry);
    }

    @Override
    protected boolean doTryAcquire() {
        final long windowId = System.currentTimeMillis() / windowMillis;
        final String key = keyBase + ':' + windowId;
        final long count = client.incrementAndExpire(key, windowMillis);
        return count <= limitForPeriod;
    }

    @Override
    public String toString() {
        return "FixedWindowRateLimiter{name='" + name + '\''
            + ", keyBase='" + keyBase + '\''
            + ", limitForPeriod=" + limitForPeriod
            + ", windowMillis=" + windowMillis
            + '}';
    }
}
