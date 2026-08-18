package io.koraframework.resilient.distributed.ratelimiter;

import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetry;
import org.jspecify.annotations.Nullable;

/**
 * Token bucket distributed rate limiter implemented with GCRA (Generic Cell Rate Algorithm) and no server-side scripting.
 *
 * <p>The whole bucket is a single value — the theoretical arrival time (TAT), the wall-clock instant the bucket would
 * next be empty, stored in milliseconds. Refill is encoded as "advance the TAT by one emission interval," which maps
 * onto an atomic {@code INCRBY}:
 * <ul>
 *   <li>{@code addAndExpire(key, +interval)} books the next slot and returns the new TAT;</li>
 *   <li>if that returns exactly {@code interval} the key was absent, so the bucket is bootstrapped to {@code now + interval};</li>
 *   <li>if the previous TAT was more than the burst tolerance ahead of now, the slot is refunded and the call rejected.</li>
 * </ul>
 *
 * <p>Steady state is exact and a single round trip. It is approximate only around idle boundaries (the fixed TTL lets an
 * idle bucket allow a bounded extra burst before it expires) and to within cross-instance clock skew, since {@code now}
 * comes from each caller's wall clock ({@link System#currentTimeMillis()}) rather than the Redis server.
 */
final class TokenBucketRateLimiter extends AbstractDistributedRateLimiter {

    private final long intervalMillis;
    private final long toleranceMillis;
    private final long ttlMillis;

    TokenBucketRateLimiter(String name,
                                  String keyBase,
                                  DistributedRateLimiterConfig config,
                                  DistributedRateLimiterClient client,
                                  @Nullable RateLimiterTelemetry telemetry) {
        super(name, keyBase, config, client, telemetry);
        this.intervalMillis = Math.max(1L, windowMillis / Math.max(1, limitForPeriod));
        this.toleranceMillis = Math.max(0L, (limitForPeriod - 1L) * intervalMillis);
        // expire when a full bucket would have drained; keeps live buckets alive as writes refresh the TTL
        this.ttlMillis = toleranceMillis + intervalMillis;
    }

    @Override
    protected boolean doTryAcquire() {
        final long now = System.currentTimeMillis();
        final long newTat = client.addAndExpire(keyBase, intervalMillis, ttlMillis);
        if (newTat == intervalMillis) {
            // key was absent (incremented from 0): bootstrap the bucket at now + interval
            client.set(keyBase, now + intervalMillis, ttlMillis);
            return true;
        }

        final long previousTat = newTat - intervalMillis;
        if (previousTat - now > toleranceMillis) {
            client.addAndExpire(keyBase, -intervalMillis, ttlMillis); // over budget: refund the slot
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "TokenBucketRateLimiter{name='" + name + '\''
            + ", keyBase='" + keyBase + '\''
            + ", limitForPeriod=" + limitForPeriod
            + ", windowMillis=" + windowMillis
            + '}';
    }
}
