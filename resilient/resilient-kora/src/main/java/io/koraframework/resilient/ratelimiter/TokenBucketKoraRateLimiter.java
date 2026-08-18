package io.koraframework.resilient.ratelimiter;

import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetry;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Token bucket rate limiter implemented with the Generic Cell Rate Algorithm (GCRA).
 *
 * <p>Instead of storing {@code (tokens, lastRefill)}, the whole bucket is a single value — the theoretical arrival time
 * (TAT), the instant the bucket would next be exactly empty. Each admission is a single CAS on one {@link AtomicLong}:
 * cheap, exact, lock-free, and allocation-free, with rejections short-circuiting before any write.
 *
 * <p>Refill is continuous at {@code limitForPeriod / limitRefreshPeriod}, with a burst of up to {@code limitForPeriod}
 * permits after an idle period — the smoothest of the three algorithms and the general default.
 */
final class TokenBucketKoraRateLimiter extends AbstractKoraRateLimiter {

    /** Emission interval: nanos between two permits at the steady rate. */
    private final long intervalNanos;
    /** Burst tolerance: how far ahead of "now" the TAT may sit, i.e. (limitForPeriod - 1) intervals. */
    private final long toleranceNanos;
    private final AtomicLong theoreticalArrivalNanos = new AtomicLong(System.nanoTime());

    TokenBucketKoraRateLimiter(String name, RateLimiterConfig config, RateLimiterTelemetry telemetry) {
        super(name, config, telemetry);
        this.intervalNanos = Math.max(1L, windowNanos / Math.max(1, limitForPeriod));
        this.toleranceNanos = Math.max(0L, (limitForPeriod - 1L) * intervalNanos);
    }

    @Override
    protected boolean doTryAcquire() {
        final long now = System.nanoTime();
        while (true) {
            final long prev = theoreticalArrivalNanos.get();
            final long base = Math.max(prev, now); // an idle bucket collapses back to 'now'
            if (base - now > toleranceNanos) {
                return false; // would exceed the burst — reject without writing
            }
            if (theoreticalArrivalNanos.compareAndSet(prev, base + intervalNanos)) {
                return true;
            }
        }
    }

    @Override
    public String toString() {
        final long now = System.nanoTime();
        final long ahead = Math.max(0L, theoreticalArrivalNanos.get() - now);
        final long availablePermits = Math.max(0L, limitForPeriod - ahead / intervalNanos);
        return "TokenBucketKoraRateLimiter{name='" + name + '\''
            + ", enabled=" + enabled
            + ", limitForPeriod=" + limitForPeriod
            + ", availablePermits=" + availablePermits
            + '}';
    }
}
